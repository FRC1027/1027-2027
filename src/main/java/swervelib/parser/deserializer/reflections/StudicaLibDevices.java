package swervelib.parser.deserializer.reflections;

import org.wpilib.util.Pair;
import org.wpilib.units.measure.Angle;
import java.util.function.Supplier;
import swervelib.parser.json.SwerveDriveJson.GyroAxis;

/**
 * StudicaLib Devices stub.
 */
public class StudicaLibDevices
{
  public static Pair<Supplier<Angle>, Object> getGyroAngle(int canid, String canbus, GyroAxis axis, boolean inverted)
  {
    throw new UnsupportedOperationException("Studica vendordep is not installed");
  }
}
