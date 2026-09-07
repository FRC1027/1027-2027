package swervelib.parser.deserializer.reflections;

import org.wpilib.util.Pair;
import org.wpilib.units.measure.Angle;
import java.util.function.Supplier;

/**
 * AndyMark Devices stub.
 */
public class AndymarkDevices
{
  public static Pair<Supplier<Angle>, Object> getAbsoluteEncoder(int canid, String canbus, boolean inverted)
  {
    throw new UnsupportedOperationException("AndyMark vendordep is not installed");
  }
}
