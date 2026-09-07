package frc.robot.commands;

import org.wpilib.math.controller.PIDController;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.networktables.DoubleSubscriber;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.command2.Command;
import frc.robot.subsystems.swervedrive.SwerveSubsystem;
import java.util.function.DoubleSupplier;

public class AimAtHubCommand extends Command {

    private final SwerveSubsystem swerve;
    private final DoubleSupplier translationXSupplier;
    private final DoubleSupplier translationYSupplier;
    private final boolean isMoving;

    private final Translation2d BLUE_HUB_CENTER = new Translation2d(4.626, 4.035);
    private final double FIELD_LENGTH_METERS = 16.541; 

    private final PIDController thetaController = new PIDController(5.0, 0.0, 0.1);

    // Pre-allocated NT4 Subscribers for shoot-on-the-move tunables
    private final DoubleSubscriber ballVelocitySub = NetworkTableInstance.getDefault()
            .getTable("SmartDashboard")
            .getDoubleTopic("Shooter/BallVelocityMPS")
            .subscribe(12.0);

    private final DoubleSubscriber latencySub = NetworkTableInstance.getDefault()
            .getTable("SmartDashboard")
            .getDoubleTopic("Shooter/SystemLatency")
            .subscribe(0.15);

    public AimAtHubCommand(SwerveSubsystem swerve, DoubleSupplier xSupplier, DoubleSupplier ySupplier, boolean isMoving) {
        this.swerve = swerve;
        this.translationXSupplier = xSupplier;
        this.translationYSupplier = ySupplier;
        this.isMoving = isMoving;
        
        addRequirements(swerve);
        thetaController.enableContinuousInput(-Math.PI, Math.PI); 
    }

    @Override
    public void execute() {
        Pose2d currentPose = swerve.getPose();
        Translation2d targetHub = BLUE_HUB_CENTER;

        var alliance = MatchState.getAlliance();
        if (alliance.isPresent() && alliance.get() == Alliance.RED) {
            targetHub = new Translation2d(FIELD_LENGTH_METERS - BLUE_HUB_CENTER.getX(), BLUE_HUB_CENTER.getY());
        }

        // VIRTUAL TARGET MATH
        if (isMoving) {
            double distanceMeters = currentPose.getTranslation().getDistance(targetHub);
            
            // Read tunables via NT4 subscribers
            double ballVelocity = ballVelocitySub.get();
            double timeOfFlight = distanceMeters / (ballVelocity > 0.0 ? ballVelocity : 12.0);
            double totalTime = timeOfFlight + latencySub.get();
            
            // Pull field velocity directly from swerve
            ChassisVelocities speeds = swerve.getFieldVelocity();
            double deltaX = speeds.vx * totalTime;
            double deltaY = speeds.vy * totalTime;
            
            // Shift the target opposite of robot movement
            targetHub = new Translation2d(targetHub.getX() - deltaX, targetHub.getY() - deltaY);
        }

        Translation2d difference = targetHub.minus(currentPose.getTranslation());
        
        // WPILib 2027 getAngle() returns Optional<Rotation2d>
        Rotation2d targetAngle = difference.getAngle().orElseGet(currentPose::getRotation);

        double rotationSpeed = thetaController.calculate(
            currentPose.getRotation().getRadians(), 
            targetAngle.getRadians()
        );

        swerve.drive(
            new Translation2d(translationXSupplier.getAsDouble(), translationYSupplier.getAsDouble()), 
            rotationSpeed, 
            true 
        );
    }

    @Override
    public void end(boolean interrupted) {
        swerve.drive(new Translation2d(0, 0), 0, true);
    }
}