package swervelib.parser.deserializer.reflections;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.CANcoderConfigurator;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.Pigeon2;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.hardware.TalonFXS;
import com.ctre.phoenix6.signals.SensorDirectionValue;
import org.wpilib.util.Pair;
import org.wpilib.math.system.DCMotor;
import org.wpilib.units.measure.Angle;
import java.util.function.Supplier;
import swervelib.parser.json.SwerveDriveJson.GyroAxis;
import yams.motorcontrollers.SmartMotorController;
import yams.motorcontrollers.SmartMotorControllerConfig;
import yams.motorcontrollers.remote.TalonFXSWrapper;
import yams.motorcontrollers.remote.TalonFXWrapper;

/**
 * Reflection class for {@link TalonFX} and {@link TalonFXS}s and other devices from CTRE.
 */
public class CTREDevices
{

  /**
   * Motor controller types.
   */
  public enum MotorControllerType
  {
    /**
     * {@link com.ctre.phoenix6.hardware.TalonFX}
     */
    TALONFX,
    /**
     * {@link com.ctre.phoenix6.hardware.TalonFXS}
     */
    TALONFXS
  }

  /**
   * Get the {@link MotorControllerType} as a {@link SmartMotorController}.
   *
   * @param canid               CAN ID of the {@link MotorControllerType}
   * @param canbus              CAN bus name of the {@link MotorControllerType}
   * @param config              {@link SmartMotorControllerConfig} to apply to the {@link SmartMotorController}
   * @param motor               {@link DCMotor} to use with the {@link SmartMotorController}
   * @param motorControllerType Motor controller type.
   * @return {@link SmartMotorController}
   */
  private static CANBus getCANBus(String canbus)
  {
    return (canbus == null || canbus.isBlank()) ? new CANBus() : new CANBus(canbus);
  }

  public static SmartMotorController getMotorController(int canid, String canbus, SmartMotorControllerConfig config,
                                                        DCMotor motor, String motorControllerType)
  {
    // Will throw an error if invalid motor controller type is given.
    var motorType = MotorControllerType.valueOf(motorControllerType.toUpperCase());
    switch (motorType)
    {
      case TALONFX ->
      {
        var motorController = new TalonFX(canid, getCANBus(canbus));
        return new TalonFXWrapper(motorController, motor, config);
      }
      case TALONFXS ->
      {
        var motorController = new TalonFXS(canid, getCANBus(canbus));
        return new TalonFXSWrapper(motorController, motor, config);
      }
    }
    throw new RuntimeException(
        "MotorType not defined. Unsure how you got here... really shouldn't be possible. Here's a cookie (;;)");
  }

  /**
   * Get the gyroscope angle supplier and gyroscope object.
   *
   * @param canid    CAN ID of the gyroscope.
   * @param canbus   CAN bus name of the gyroscope.
   * @param axis     Gyro axis.
   * @param inverted Inverted gyro readings.
   * @return {@link Pair} of {@link Supplier} and {@link Object}
   */
  public static Pair<Supplier<Angle>, Object> getGyroAngle(int canid, String canbus, GyroAxis axis, boolean inverted)
  {
    var gyro = new Pigeon2(canid, getCANBus(canbus));
    switch (axis)
    {
      case YAW: return Pair.of(gyro.getYaw().asSupplier(), gyro);
      case PITCH: return Pair.of(gyro.getPitch().asSupplier(), gyro);
      case ROLL: return Pair.of(gyro.getRoll().asSupplier(), gyro);
      default: throw new IllegalArgumentException("Invalid gyro axis: " + axis);
    }
  }

  /**
   * Get the {@link com.ctre.phoenix6.hardware.CANcoder} angle.
   *
   * @param canid    CAN ID of the encoder.
   * @param canbus   CAN bus name of the encoder.
   * @param inverted Inverted encoder readings.
   * @return {@link Supplier} of {@link Angle} and {@link com.ctre.phoenix6.hardware.CANcoder}
   */
  public static Pair<Supplier<Angle>, Object> getAbsoluteEncoder(int canid, String canbus, boolean inverted)
  {
    var                   encoder      = new CANcoder(canid, getCANBus(canbus));
    CANcoderConfiguration cfg          = new CANcoderConfiguration();
    CANcoderConfigurator  configurator = encoder.getConfigurator();
    configurator.refresh(cfg);
    cfg.MagnetSensor.withSensorDirection(
        inverted ? SensorDirectionValue.Clockwise_Positive : SensorDirectionValue.CounterClockwise_Positive);
    configurator.apply(cfg);
    return Pair.of(() -> encoder.getPosition().getValue(), encoder);
  }
}
