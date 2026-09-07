package swervelib.parser.deserializer.reflections;

import org.wpilib.util.Pair;
import org.wpilib.math.system.DCMotor;
import org.wpilib.units.measure.Angle;
import java.util.function.Supplier;
import yams.motorcontrollers.SmartMotorController;
import yams.motorcontrollers.SmartMotorControllerConfig;

/**
 * ThriftyBot Devices stub.
 */
public class ThriftyBotDevices
{
  public enum MotorControllerType
  {
    NOVA
  }

  public enum AbsoluteEncoderType
  {
    THRIFTY,
    REV,
    THROUGHBORE,
    SRXMAG,
    DUTYCYCLE
  }

  public static SmartMotorController getMotorController(int canid, String canbus, SmartMotorControllerConfig config,
                                                        DCMotor motor, String motorControllerType)
  {
    throw new UnsupportedOperationException("ThriftyNova is not supported in this configuration");
  }

  public static Pair<Supplier<Angle>, Object> getAbsoluteEncoder(int canid, String canbus, boolean inverted)
  {
    throw new UnsupportedOperationException("ThriftyEncoder is not supported in this configuration");
  }

  public static Pair<Supplier<Angle>, Object> getAttachedAbsoluteEncoder(String attachType, Object motorController,
                                                                         boolean inverted)
  {
    throw new UnsupportedOperationException("ThriftyNova attached absolute encoder is not supported");
  }
}
