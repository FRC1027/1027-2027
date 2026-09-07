package frc.robot.subsystems;

import org.wpilib.math.util.Units;
import org.wpilib.networktables.DoubleArrayEntry;
import org.wpilib.networktables.DoubleArraySubscriber;
import org.wpilib.networktables.DoubleEntry;
import org.wpilib.networktables.DoubleSubscriber;
import org.wpilib.networktables.NetworkTable;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.networktables.StringSubscriber;
import org.wpilib.system.Timer;
import org.wpilib.command2.SubsystemBase;

import frc.robot.util.Constants.ObjectRecognitionConstants;

public class VisionSubsystem extends SubsystemBase {
    private final String limelightName;
    private final NetworkTable limelight;
    private int pipelineIndex;

    // NT4 Subscribers & Control Entries
    private final DoubleSubscriber tvSub;
    private final DoubleSubscriber tidSub;
    private final DoubleArraySubscriber targetPoseSub;
    private final DoubleEntry pipelineEntry;
    private final DoubleEntry ledModeEntry;
    private final DoubleArrayEntry fiducialFilterEntry;
    private final StringSubscriber classSub;

    /* Instance variables to store AprilTag/Fiducial data */
    private double fiducialID;
    private double fiducialTX;
    private double fiducialTY;
    private double fiducialRawTZ;
    private double fiducialAdjustedTZ;
    private double fiducialDistToCamera;
    private double fiducialHorizontalDistToRobot;
    private double fiducialHorizontalDistToCamera;
    private boolean hasTarget;

    /* Instance variables to store neural network detection data */
    private String neuralClassName = "";
    private double neuralConfidence;
    private double neuralTX;
    private double neuralTY;
    private double neuralTZ;
    private double neuralDistToCamera;
    private double neuralHorizontalDistToRobot;
    private double neuralHorizontalDistToCamera;

    private double lastSeenTime = 0.0;

    public VisionSubsystem(String limelightName, int pipelineIndex, int[] desiredTagIDs) {
        this.limelightName = limelightName;
        this.pipelineIndex = pipelineIndex;

        this.limelight = NetworkTableInstance.getDefault().getTable(limelightName);

        // Bind NT4 topics
        this.tvSub = limelight.getDoubleTopic("tv").subscribe(0.0);
        this.tidSub = limelight.getDoubleTopic("tid").subscribe(-1.0);
        this.targetPoseSub = limelight.getDoubleArrayTopic("targetpose_cameraspace").subscribe(new double[0]);
        this.classSub = limelight.getStringTopic("tcclass").subscribe("");

        this.pipelineEntry = limelight.getDoubleTopic("pipeline").getEntry(0.0);
        this.ledModeEntry = limelight.getDoubleTopic("ledMode").getEntry(0.0);
        this.fiducialFilterEntry = limelight.getDoubleArrayTopic("fiducial_id_filters").getEntry(new double[0]);

        // Force LEDs off (1 = Force Off, 0 = Pipeline Default, 2 = Force Blink, 3 = Force On)
        ledModeEntry.set(1.0);

        setPipelineIndex(pipelineIndex);
        if (desiredTagIDs != null && desiredTagIDs.length > 0) {
            setDesiredTagIDs(desiredTagIDs);
        }
    }

    @Override
    public void periodic() {
        double currentTime = Timer.getTimestamp();
        boolean currentHasTarget = tvSub.get() >= 1.0;

        if (pipelineIndex == 0) {
            // AprilTag pipeline
            if (currentHasTarget) {
                hasTarget = true;
                lastSeenTime = currentTime;

                double[] pose = targetPoseSub.get();
                if (pose != null && pose.length >= 3) {
                    fiducialTX = pose[0];
                    fiducialTY = pose[1];
                    fiducialRawTZ = Units.metersToInches(pose[2]);

                    fiducialAdjustedTZ = -0.0000126596 * Math.pow(fiducialRawTZ, 3) 
                                       + 0.00572852 * Math.pow(fiducialRawTZ, 2) 
                                       + 0.311561 * fiducialRawTZ 
                                       + 24.53905;

                    fiducialDistToCamera = Math.sqrt(fiducialTX * fiducialTX + fiducialTY * fiducialTY + fiducialRawTZ * fiducialRawTZ);

                    double mountAngle = ObjectRecognitionConstants.LIMELIGHT_MOUNT_ANGLE_RADIANS;
                    double zWorld = fiducialRawTZ * Math.cos(mountAngle) - fiducialTY * Math.sin(mountAngle);
                    fiducialHorizontalDistToCamera = Math.sqrt(fiducialTX * fiducialTX + zWorld * zWorld);

                    fiducialHorizontalDistToRobot = fiducialDistToCamera - ObjectRecognitionConstants.CAMERA_TO_BUMPER_DISTANCE;
                }

                fiducialID = tidSub.get();
            } else {
                if (currentTime - lastSeenTime < ObjectRecognitionConstants.LIMELIGHT_TARGET_TIMEOUT) {
                    hasTarget = true;
                } else {
                    hasTarget = false;
                    fiducialID = -1.0;
                    fiducialTX = 0.0;
                    fiducialTY = 0.0;
                    fiducialRawTZ = 0.0;
                    fiducialAdjustedTZ = 0.0;
                    fiducialDistToCamera = 0.0;
                    fiducialHorizontalDistToRobot = 0.0;
                    fiducialHorizontalDistToCamera = 0.0;
                }
            }
        } else {
            // Neural detector pipeline
            if (currentHasTarget) {
                hasTarget = true;
                lastSeenTime = currentTime;

                neuralClassName = classSub.get();

                double[] pose = targetPoseSub.get();
                if (pose != null && pose.length >= 3) {
                    neuralTX = pose[0];
                    neuralTY = pose[1];
                    neuralTZ = pose[2];

                    neuralDistToCamera = Math.sqrt(neuralTX * neuralTX + neuralTY * neuralTY + neuralTZ * neuralTZ);

                    double mountAngle = ObjectRecognitionConstants.LIMELIGHT_MOUNT_ANGLE_RADIANS;
                    double zWorld = neuralTZ * Math.cos(mountAngle) - neuralTY * Math.sin(mountAngle);
                    neuralHorizontalDistToCamera = Math.sqrt(neuralTX * neuralTX + zWorld * zWorld);

                    neuralHorizontalDistToRobot = neuralDistToCamera - ObjectRecognitionConstants.CAMERA_TO_BUMPER_DISTANCE;
                }
            } else {
                if (currentTime - lastSeenTime < ObjectRecognitionConstants.LIMELIGHT_TARGET_TIMEOUT) {
                    hasTarget = true;
                } else {
                    hasTarget = false;
                    neuralClassName = "";
                    neuralConfidence = 0.0;
                    neuralTX = 0.0;
                    neuralTY = 0.0;
                    neuralTZ = 0.0;
                }
            }
        }
    }

    public NetworkTable getLimelight() {
        return limelight;
    }

    public int getPipelineIndex() {
        return pipelineIndex;
    }

    public double getFiducialID() {
        return fiducialID;
    }

    public double getFiducialTX() {
        return fiducialTX;
    }

    public double getFiducialTY() {
        return fiducialTY;
    }

    public double getFiducialRawTZ() {
        return fiducialRawTZ;
    }

    public double getFiducialAdjustedTZ() {
        return fiducialAdjustedTZ;
    }

    public double getFiducialDistToCamera() {
        return fiducialDistToCamera;
    }

    public double getFiducialHorizontalDistToRobot() {
        return fiducialHorizontalDistToRobot;
    }

    public double getFiducialHorizontalDistToCamera() {
        return fiducialHorizontalDistToCamera;
    }

    public boolean hasTarget() {
        return hasTarget;
    }

    public String getNeuralClassName() {
        return neuralClassName;
    }

    public double getNeuralConfidence() {
        return neuralConfidence;
    }

    public double getNeuralTX() {
        return neuralTX;
    }

    public double getNeuralTY() {
        return neuralTY;
    }

    public double getNeuralTZ() {
        return neuralTZ;
    }

    public double getNeuralDistToCamera() {
        return neuralDistToCamera;
    }

    public double getNeuralHorizontalDistToRobot() {
        return neuralHorizontalDistToRobot;
    }

    public double getNeuralHorizontalDistToCamera() {
        return neuralHorizontalDistToCamera;
    }

    public void setPipelineIndex(int pipelineIndex) {
        this.pipelineIndex = pipelineIndex;
        pipelineEntry.set(pipelineIndex);
    }

    public void setDesiredTagIDs(int[] desiredTagIDs) {
        if (desiredTagIDs == null) return;
        double[] doubleIds = new double[desiredTagIDs.length];
        for (int i = 0; i < desiredTagIDs.length; i++) {
            doubleIds[i] = desiredTagIDs[i];
        }
        fiducialFilterEntry.set(doubleIds);
    }
}