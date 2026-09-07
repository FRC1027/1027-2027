package frc.robot.commands;

import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.util.Units;
import org.wpilib.networktables.DoubleArraySubscriber;
import org.wpilib.networktables.DoubleSubscriber;
import org.wpilib.networktables.NetworkTable;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.networktables.StringPublisher;
import org.wpilib.command2.Command;

import frc.robot.subsystems.VisionSubsystem;
import frc.robot.subsystems.swervedrive.SwerveSubsystem;
import frc.robot.util.Constants.ObjectRecognitionConstants;

/**
 * A command that detects either an AprilTag or a game piece and then either aligns to the target
 * or drives the robot toward it.
 */
public class DriveTowardTargetCommand extends Command {
    private final VisionSubsystem visionSubsystem;
    private final SwerveSubsystem drivebase;

    private final double STOP_DISTANCE = Units.inchesToMeters(48.0);
    private final double maxSpeed = 1.0; 
    private final double maxRotation = 1.0; 

    private double distanceToTarget;
    private double tx;
    private boolean alignOnly;
    private boolean hasTarget;

    private double forwardSpeed = 0.0;
    private double rotationSpeed = 0.0;

    // Zero-allocation NT4 Subscribers for Limelight data
    private final DoubleSubscriber tvSub;
    private final DoubleArraySubscriber poseSub;

    // Zero-allocation NT4 Publisher for status telemetry
    private final StringPublisher statusPub;

    public DriveTowardTargetCommand(SwerveSubsystem drivebase, VisionSubsystem visionSubsystem, boolean alignOnly) {
        this.drivebase = drivebase;
        this.visionSubsystem = visionSubsystem;
        this.alignOnly = alignOnly;

        addRequirements(drivebase);

        NetworkTable limelight = NetworkTableInstance.getDefault().getTable(ObjectRecognitionConstants.LIMELIGHT_NAME);
        this.tvSub = limelight.getDoubleTopic("tv").subscribe(0.0);
        this.poseSub = limelight.getDoubleArrayTopic("targetpose_cameraspace").subscribe(new double[0]);

        this.statusPub = NetworkTableInstance.getDefault()
                .getTable("SmartDashboard")
                .getStringTopic("LL Status/Error Type")
                .publish();
    }

    @Override
    public void initialize() {
        if (visionSubsystem.getPipelineIndex() == ObjectRecognitionConstants.APRIL_TAG_PIPELINE_INDEX) {
            visionSubsystem.setPipelineIndex(ObjectRecognitionConstants.APRIL_TAG_PIPELINE_INDEX);
        } else {
            visionSubsystem.setPipelineIndex(ObjectRecognitionConstants.OBJECT_DETECTION_PIPELINE_INDEX);
        }

        distanceToTarget = 0.0;
        tx = 0.0;
        hasTarget = false;
    }

    @Override
    public void execute() {
        if (visionSubsystem.getPipelineIndex() == ObjectRecognitionConstants.APRIL_TAG_PIPELINE_INDEX) {
            double fid = visionSubsystem.getFiducialID();
            if (Double.isNaN(fid) || fid < 0.0) {
                resetAndStop();
                return;
            }
        }

        // Check target validity flag
        double tv = tvSub.get();
        if (tv < 1.0) {
            resetAndStop();
            return;
        }
        hasTarget = true;

        // Read camera-space pose
        double[] pose = poseSub.get();
        if (pose == null || pose.length < 3) {
            resetAndStop();
            return;
        }

        tx = pose[0];

        if (tx > 0) {
            tx = pose[0] + Units.inchesToMeters(3.25);
        } else if (tx < 0) {
            tx = pose[0] - Units.inchesToMeters(3.25);
        }

        if (visionSubsystem.getPipelineIndex() == ObjectRecognitionConstants.APRIL_TAG_PIPELINE_INDEX) {
            distanceToTarget = visionSubsystem.getFiducialHorizontalDistToRobot();
        } else {
            distanceToTarget = visionSubsystem.getNeuralHorizontalDistToRobot();
        }

        // Forward speed control
        if (maxSpeed > 0 && distanceToTarget > STOP_DISTANCE) {
            double speedFactor = Math.min(1.0, distanceToTarget / 4.0);
            forwardSpeed = maxSpeed * speedFactor;
        } else {
            forwardSpeed = 0.0;
        }

        // Rotation control
        double kP_turn = 4.0;
        rotationSpeed = -kP_turn * tx;
        rotationSpeed = Math.max(-maxRotation, Math.min(maxRotation, rotationSpeed));

        drivebase.drive(new Translation2d(forwardSpeed, 0), rotationSpeed, true);
    }

    @Override
    public void end(boolean interrupted) {
        stopRobot();
        statusPub.set(interrupted ? "Interrupted" : "Arrived at Target");
        System.out.println("[DriveTowardTarget] Ended");
    }

    @Override
    public boolean isFinished() {
        final double ROTATION_TOLERANCE = 0.1;

        if (!hasTarget) {
            return true;
        }

        boolean reachedDistanceTarget = (maxSpeed > 0) && (distanceToTarget <= STOP_DISTANCE);
        boolean alignedTarget = Math.abs(tx) <= ROTATION_TOLERANCE;

        return reachedDistanceTarget || alignedTarget;
    }

    private void resetAndStop() {
        distanceToTarget = 0.0;
        tx = 0.0;
        hasTarget = false;
        stopRobot();
    }

    private void stopRobot() {
        drivebase.drive(new Translation2d(0.0, 0.0), 0.0, true);
    }

    public void setPipelineToAprilTags() {
        visionSubsystem.setPipelineIndex(ObjectRecognitionConstants.APRIL_TAG_PIPELINE_INDEX);
    }

    public void setPipelineToObjectDetection() {
        visionSubsystem.setPipelineIndex(ObjectRecognitionConstants.OBJECT_DETECTION_PIPELINE_INDEX);
    }
}