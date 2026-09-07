package frc.robot.subsystems;

import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.DutyCycleOut;
import com.ctre.phoenix6.controls.NeutralOut;
import com.ctre.phoenix6.controls.StrictFollower;
import com.ctre.phoenix6.controls.VelocityVoltage;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;
import com.ctre.phoenix6.CANBus;

import org.wpilib.networktables.DoublePublisher;
import org.wpilib.networktables.DoubleSubscriber;
import org.wpilib.networktables.NetworkTable;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.SubsystemBase;

import frc.robot.subsystems.swervedrive.SwerveSubsystem;
import frc.robot.util.Constants.ShooterConstants;
import frc.robot.util.Constants.ObjectRecognitionConstants;
import frc.robot.util.ShooterInterpolationTable;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.Alliance;

/**
 * Subsystem that controls the shooter flywheels and shot execution commands.
 */
public class ShooterSubsystem extends SubsystemBase {
    private final NetworkTable limelight = NetworkTableInstance.getDefault().getTable(ObjectRecognitionConstants.LIMELIGHT_NAME);

    private final IndexerSubsystem m_indexer;
    private final VisionSubsystem visionSubsystem;

    // Primary shooter motor controller (leader) and follower
    // Pass "" or "rio" for the default CAN bus
    private final TalonFX shooterMotor1 = new TalonFX(ShooterConstants.SHOOTER_MOTOR_ID1, new CANBus(""));
    private final TalonFX shooterMotor2 = new TalonFX(ShooterConstants.SHOOTER_MOTOR_ID2, new CANBus(""));

    private final StrictFollower followerRequest = new StrictFollower(ShooterConstants.SHOOTER_MOTOR_ID1);
    private final DutyCycleOut dutyCycleRequest = new DutyCycleOut(0.0);

    // Odometry Targeting
    private SwerveSubsystem m_drivebase;
    private final Translation2d BLUE_HUB_CENTER = new Translation2d(4.626, 4.035);
    private final double FIELD_LENGTH_METERS = 16.541;

    // NT4 Subscribers & Publishers for tunables
    private final DoubleSubscriber velocityEfficiencySub;
    private final DoublePublisher velocityEfficiencyPub;

    private final DoubleSubscriber sweetSpotOffsetSub;
    private final DoublePublisher sweetSpotOffsetPub;

    private final DoubleSubscriber ballVelocitySub;
    private final DoublePublisher ballVelocityPub;

    private final DoubleSubscriber systemLatencySub;
    private final DoublePublisher systemLatencyPub;

    public ShooterSubsystem(IndexerSubsystem m_indexer, VisionSubsystem visionSubsystem) {
        this.m_indexer = m_indexer;
        this.visionSubsystem = visionSubsystem;

        // Configure velocity-loop gains and neutral mode
        TalonFXConfiguration config = new TalonFXConfiguration();
        config.Slot0.kP = 0.15;
        config.Slot0.kI = 0.0;
        config.Slot0.kD = 0.0;
        config.MotorOutput.NeutralMode = NeutralModeValue.Coast;

        shooterMotor1.getConfigurator().apply(config);

        config.MotorOutput.Inverted = InvertedValue.Clockwise_Positive;
        shooterMotor2.getConfigurator().apply(config);

        // Setup NT4 tunables
        NetworkTable table = NetworkTableInstance.getDefault().getTable("SmartDashboard");
        
        velocityEfficiencySub = table.getDoubleTopic("Shooter/VelocityEfficiency").subscribe(ShooterConstants.VELOCITY_EFFICIENCY);
        velocityEfficiencyPub = table.getDoubleTopic("Shooter/VelocityEfficiency").publish();
        velocityEfficiencyPub.set(ShooterConstants.VELOCITY_EFFICIENCY);

        sweetSpotOffsetSub = table.getDoubleTopic("Shooter/SweetSpotOffset").subscribe(0.3);
        sweetSpotOffsetPub = table.getDoubleTopic("Shooter/SweetSpotOffset").publish();

        ballVelocitySub = table.getDoubleTopic("Shooter/BallVelocityMPS").subscribe(12.0);
        ballVelocityPub = table.getDoubleTopic("Shooter/BallVelocityMPS").publish();

        systemLatencySub = table.getDoubleTopic("Shooter/SystemLatency").subscribe(0.15);
        systemLatencyPub = table.getDoubleTopic("Shooter/SystemLatency").publish();
    }

    public Command testDistanceAutomatic() {
        return Commands.run(() -> {
            visionSubsystem.periodic();
            double distToCamera = visionSubsystem.getFiducialDistToCamera();
            double horizontalDistToCamera = visionSubsystem.getFiducialHorizontalDistToCamera();
            System.out.println("Dist to Camera: " + distToCamera + " Horizontal Dist to Camera: " + horizontalDistToCamera);
        });
    }

    public double calculateTheoreticalRPS() {
        double shooterToTag = visionSubsystem.getFiducialHorizontalDistToCamera() - ShooterConstants.SHOOTER_TO_LIMELIGHT_OFFSET;

        if (!Double.isFinite(shooterToTag)) {
            return 0.0;
        }

        if (shooterToTag * Math.tan(ShooterConstants.SHOOTER_ANGLE) <= ShooterConstants.HEIGHT_DIFFERENCE) {
            return 0.0;
        }

        double velocity = Math.sqrt((ShooterConstants.GRAVITY_CONSTANT * shooterToTag * shooterToTag) / 
                                    (2 * Math.cos(ShooterConstants.SHOOTER_ANGLE) * Math.cos(ShooterConstants.SHOOTER_ANGLE) * 
                                    (shooterToTag * Math.tan(ShooterConstants.SHOOTER_ANGLE) - ShooterConstants.HEIGHT_DIFFERENCE)));

        double efficiency = velocityEfficiencySub.get();
        double adjustedVelocity = velocity * efficiency;
        double rps = adjustedVelocity / (2 * Math.PI * ShooterConstants.SHOOTER_WHEEL_RADIUS);

        return rps;
    }

    public double calculateTableRPS(double shooterToTag) {
        return ShooterInterpolationTable.getRPS(shooterToTag);
    }

    public void setShooterRPS() {
        double wheelRPS = calculateTheoreticalRPS();

        if (!Double.isFinite(wheelRPS) || wheelRPS == 0.0) {
            shooterMotor1.setControl(new NeutralOut());
            shooterMotor2.setControl(followerRequest);
            return;
        }

        double motorRPS = wheelRPS * ShooterConstants.GEAR_RATIO;
        shooterMotor1.setControl(new VelocityVoltage(motorRPS));
        shooterMotor2.setControl(followerRequest);
    }

    public Command shoot() {
        return Commands.deadline(
            runEnd(
                this::setShooterRPS,
                () -> {
                    shooterMotor1.setControl(new NeutralOut());
                    shooterMotor2.setControl(followerRequest);
                }
            ), 
            m_indexer.runIndexerCommand()
        );
    }

    public Command fullSpeed() {
        return Commands.deadline(
            runEnd(
                () -> setShooterSpeed(1.0), 
                () -> {
                    shooterMotor1.setControl(new NeutralOut());
                    shooterMotor2.setControl(followerRequest);
                }
            ),
            m_indexer.runIndexerCommand());
    }

    public Command stepUpDayDemo() {
        return Commands.deadline(
            runEnd(
                () -> setShooterSpeed(0.48), 
                () -> {
                    shooterMotor1.setControl(new NeutralOut());
                    shooterMotor2.setControl(followerRequest);
                }
            ),
            m_indexer.runIndexerCommand());
    }

    public void setShooterSpeed(double speed) {
        // Phoenix 6 duty-cycle output
        shooterMotor1.setControl(dutyCycleRequest.withOutput(speed));
        shooterMotor2.setControl(followerRequest);
    }

    public double getDistance() {
        return visionSubsystem.getFiducialHorizontalDistToCamera() - ShooterConstants.SHOOTER_TO_LIMELIGHT_OFFSET;
    }

    public void setDrivebase(SwerveSubsystem drivebase) {
        this.m_drivebase = drivebase;
        sweetSpotOffsetPub.set(0.3);
        ballVelocityPub.set(12.0);
        systemLatencyPub.set(0.15);
    }

    public double getAdjustedTargetDistance(boolean isMoving) {
        if (m_drivebase == null) return 0.0;

        double sweetSpotOffsetMeters = sweetSpotOffsetSub.get();
        double ballVelocityMPS = ballVelocitySub.get();
        double systemLatency = systemLatencySub.get();
        Translation2d targetHub = BLUE_HUB_CENTER;

        var alliance = MatchState.getAlliance();
        if (alliance.isPresent() && alliance.get() == Alliance.RED) {
            targetHub = new Translation2d(FIELD_LENGTH_METERS - BLUE_HUB_CENTER.getX(), BLUE_HUB_CENTER.getY());
        }

        Pose2d currentPose = m_drivebase.getPose();

        if (isMoving) {
            double initialDistance = currentPose.getTranslation().getDistance(targetHub);
            double timeOfFlight = initialDistance / (ballVelocityMPS > 0.0 ? ballVelocityMPS : 12.0); 
            double totalTime = timeOfFlight + systemLatency;
            
            ChassisVelocities speeds = m_drivebase.getFieldVelocity();
            double deltaX = speeds.vx * totalTime;
            double deltaY = speeds.vy * totalTime;
            
            targetHub = new Translation2d(targetHub.getX() - deltaX, targetHub.getY() - deltaY);
        }

        double finalDistanceToCenter = currentPose.getTranslation().getDistance(targetHub);
        return Math.max(0.0, finalDistanceToCenter - sweetSpotOffsetMeters);
    }

    public double calculateWheelRPSFromOdometry(boolean isMoving) {
        double shooterToTarget = getAdjustedTargetDistance(isMoving);
        
        if (!Double.isFinite(shooterToTarget) || shooterToTarget == 0.0) {
            return 0.0;
        }

        if (shooterToTarget * Math.tan(ShooterConstants.SHOOTER_ANGLE) <= ShooterConstants.HEIGHT_DIFFERENCE) {
            return 0.0;
        }

        double velocity = Math.sqrt((ShooterConstants.GRAVITY_CONSTANT * shooterToTarget * shooterToTarget) / 
                                    (2 * Math.cos(ShooterConstants.SHOOTER_ANGLE) * Math.cos(ShooterConstants.SHOOTER_ANGLE) * (shooterToTarget * Math.tan(ShooterConstants.SHOOTER_ANGLE) - ShooterConstants.HEIGHT_DIFFERENCE)));

        double efficiency = velocityEfficiencySub.get();
        double adjustedVelocity = velocity * efficiency;
        double rps = adjustedVelocity / (2 * Math.PI * ShooterConstants.SHOOTER_WHEEL_RADIUS);

        return rps * 1.03;
    }

    public void setShooterRPSFromOdometry(boolean isMoving) {
        double wheelRPS = calculateWheelRPSFromOdometry(isMoving);

        if (!Double.isFinite(wheelRPS) || wheelRPS == 0.0) {
            shooterMotor1.setControl(new NeutralOut());
            shooterMotor2.setControl(followerRequest);
            return;
        }

        double motorRPS = wheelRPS * ShooterConstants.GEAR_RATIO;
        shooterMotor1.setControl(new VelocityVoltage(motorRPS));
        shooterMotor2.setControl(followerRequest);
    }

    public Command shootOdometry(boolean isMoving) {
        return Commands.deadline(
            runEnd(
                () -> setShooterRPSFromOdometry(isMoving), 
                () -> {
                    shooterMotor1.setControl(new NeutralOut()); 
                    shooterMotor2.setControl(followerRequest);
                }
            ), 
            m_indexer.runIndexerCommand() 
        ).until(() -> calculateWheelRPSFromOdometry(isMoving) == 0.0);
    }
}