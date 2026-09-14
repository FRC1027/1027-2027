package frc.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;

import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.hardware.bus.CANPort;

import frc.robot.util.Constants.IndexerConstants;

/**
 * Subsystem that controls the indexer motor used to feed game pieces into the shooter.
 */
public class IndexerSubsystem extends SubsystemBase {
    // Indexer motor.
    private final SparkMax indexerMotor;

    // Motor configuration for the intake motor.
    public static final SparkMaxConfig indexerConfig = new SparkMaxConfig();

    static {
        indexerConfig
            .idleMode(IdleMode.kBrake)
            .smartCurrentLimit(50);
    }
    
    /**
     * Constructor for the IndexerSubsystem. Initializes the indexer motor and applies the configuration.
     */
    public IndexerSubsystem() {
        // CAN_S0 is the default SystemCore/roboRIO internal CAN bus
        indexerMotor = new SparkMax(CANPort.CAN_S0, IndexerConstants.INDEXER_MOTOR_ID, MotorType.kBrushless);

        // Configure the indexer motor using safe parameter reset and persistent parameter storage.
        indexerMotor.configure(indexerConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    }

    /**
     * Constructs a delayed command that starts the indexer and allows the shooter to spin up before
     * feeding, while ensuring that the indexer is stopped when the command ends or is interrupted.
     * 
     * @return command that leaves the indexer stopped and forces zero output on end/interruption
     */
    public Command runIndexerCommand() {
        return Commands.sequence(
            Commands.waitSeconds(2.0),
            run(() -> setIndexerSpeed(1.0))
        ).finallyDo(interrupted -> setIndexerSpeed(0.0));
    }

    /**
     * Sets the speed of the indexer motor.
     * 
     * @param speed The speed to set the motor to (between -1.0 and 1.0).
     */
    public void setIndexerSpeed(double speed) {
        indexerMotor.setVoltage(speed * 12.0);
    }
}