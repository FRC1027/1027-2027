package frc.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.spark.ClosedLoopSlot;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkLowLevel.ControlType;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;

import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.SubsystemBase;

import frc.robot.util.Constants.HopperConstants;

import java.util.Set;

/**
 * Subsystem that controls the hopper motor used to enlarge the holding space for the balls.
 */
public class HopperSubsystem extends SubsystemBase {
    private boolean hopperEnlarged;

    private final SparkMax hopperMotor;
    private final RelativeEncoder hopperEncoder;
    private final SparkClosedLoopController hopperPIDController;

    public static final SparkMaxConfig hopperConfig = new SparkMaxConfig();

    static {
        hopperConfig
            .idleMode(IdleMode.kBrake)
            .smartCurrentLimit(50);

        hopperConfig.closedLoop
            .pid(0.1, 0.0, 0.0)
            .outputRange(-0.5, 0.5);
    }

    public HopperSubsystem() {
        hopperEnlarged = false;

        // Bus 0 is the default roboRIO CAN bus
        hopperMotor = new SparkMax(0, HopperConstants.HOPPER_MOTOR_ID1, MotorType.kBrushless);
        hopperEncoder = hopperMotor.getEncoder();
        hopperPIDController = hopperMotor.getClosedLoopController();

        hopperMotor.configure(
            hopperConfig, 
            ResetMode.kResetSafeParameters, 
            PersistMode.kPersistParameters
        );
    }

    /**
     * Reads primitive double value from the encoder signal using the Alpha-6 .get(defaultValue) method.
     */
    private double getEncoderPosition() {
        return hopperEncoder.getPosition().get(0.0);
    }

    public Command hopperEnlarger2000Command() {
        return Commands.defer(() -> {
            double initialPosition = getEncoderPosition();

            double targetArmRotations = 105.0 / 360.0;
            double targetRotations = targetArmRotations * HopperConstants.HOPPER_GEAR_RATIO;

            if (!hopperEnlarged) {
                System.out.println("Hopper is Extended: " + hopperEnlarged);

                return run(() -> setHopperSpeed(0.2))
                        .until(() -> getEncoderPosition() >= initialPosition + targetRotations)
                        .finallyDo(() -> {
                            setHopperSpeed(0.0);
                            holdPosition(initialPosition + targetRotations);
                            hopperEnlarged = true;
                            System.out.println("Hopper is Extended: " + hopperEnlarged);
                        });
            } else {
                System.out.println("Hopper is Extended: " + hopperEnlarged);

                return run(() -> setHopperSpeed(-0.2))
                        .until(() -> getEncoderPosition() <= initialPosition - targetRotations)
                        .finallyDo(() -> {
                            setHopperSpeed(0.0);
                            holdPosition(initialPosition - targetRotations);
                            hopperEnlarged = false;
                            System.out.println("Hopper is Extended: " + hopperEnlarged);
                        });
            }
        }, Set.of(this));
    }

    public Command moveHopperDown() {
        return runEnd(
            () -> setHopperSpeed(0.25),
            () -> setHopperSpeed(0.0)
        );
    }

    public Command moveHopperUp() {
        return runEnd(
            () -> setHopperSpeed(-0.25),
            () -> setHopperSpeed(0.0)
        );
    }

    public void holdPosition(double targetPosition) {
        // Updated to setSetpoint per SparkClosedLoopController signature
        hopperPIDController.setSetpoint(targetPosition, ControlType.kPosition, ClosedLoopSlot.kSlot0);
    }

    public void setHopperSpeed(double speed) {
        hopperMotor.setVoltage(speed * 12.0);
    }

    public boolean getHopperEnlarged() {
        return hopperEnlarged;
    }
}