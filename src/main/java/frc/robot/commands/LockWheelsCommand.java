package frc.robot.commands;

import org.wpilib.networktables.StringPublisher;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.command2.Command;
import frc.robot.subsystems.swervedrive.SwerveSubsystem;

/**
 * Command that locks the swerve modules in place to resist robot movement.
 */
public class LockWheelsCommand extends Command {
    private final SwerveSubsystem swerve;

    // NT4 String Publisher initialized once for zero-allocation performance
    private final StringPublisher lockStatusPub = NetworkTableInstance.getDefault()
            .getTable("SmartDashboard")
            .getStringTopic("Wheel Lock Status")
            .publish();

    /**
     * Creates a wheel-lock command for the provided swerve subsystem.
     *
     * @param swerve the drivebase to lock
     */
    public LockWheelsCommand(SwerveSubsystem swerve) {
        this.swerve = swerve;
        addRequirements(swerve);
    }

    /**
     * Publishes lock state when the command starts.
     */
    @Override
    public void initialize() {
        // Runs once when the command is scheduled.
        lockStatusPub.set("Wheels are Now Locked");
    }

    /**
     * Continuously commands the wheel-lock stance while active.
     */
    @Override
    public void execute() {
        // Runs repeatedly while the command is active (about every 20 ms).
        swerve.lock();

        lockStatusPub.set("Wheels are Locked");
    }

    /**
     * Publishes unlock state when the command ends.
     *
     * @param interrupted true if the command was interrupted
     */
    @Override
    public void end(boolean interrupted) {
        // Runs when the command finishes or is interrupted.
        lockStatusPub.set("Wheels are Now Unlocked");
    }

    /**
     * Keeps the command running until it is externally interrupted.
     *
     * @return always false
     */
    @Override
    public boolean isFinished() {
        // This command never finishes on its own; it runs until interrupted.
        return false;
    }
}