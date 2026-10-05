package swervelib.parser.deserializer.reflections;


import static org.wpilib.units.Units.Rotations;

import com.reduxrobotics.canandgyro.Canandgyro;
import com.reduxrobotics.canandmag.Canandmag;
import org.wpilib.util.Pair;
import org.wpilib.units.measure.Angle;
import java.util.function.Supplier;
import swervelib.parser.json.SwerveDriveJson.GyroAxis;

/**
 * Reflective class for {@link com.reduxrobotics.canandgyro.Canandgyro} and other devices.
 */
public class ReduxDevices
{

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
    var gyro = new Canandgyro(canid, canbus);
    return switch (axis)
    {
      case YAW -> Pair.of(() -> Rotations.of(gyro.getYaw() * (inverted ? -1 : 1)), gyro);
      case PITCH -> Pair.of(() -> Rotations.of(gyro.getPitch() * (inverted ? -1 : 1)), gyro);
      case ROLL -> Pair.of(() -> Rotations.of(gyro.getRoll() * (inverted ? -1 : 1)), gyro);
      default -> throw new IllegalArgumentException("Invalid gyro axis: " + axis);
    };
  }

  /**
   * Get the {@link com.reduxrobotics.sensors.canandmag.Canandmag} angle.
   *
   * @param canid    CAN ID of the encoder.
   * @param canbus   CAN bus name of the encoder.
   * @param inverted Inverted encoder readings.
   * @return {@link Supplier} of {@link Angle} and {@link com.reduxrobotics.sensors.canandmag.Canandmag}
   */
  public static Pair<Supplier<Angle>, Object> getAbsoluteEncoder(int canid, String canbus, boolean inverted)
  {
    var encoder = new Canandmag(canid, canbus);
    encoder.setSettings(encoder.getSettings().setInvertDirection(inverted));
    return Pair.of(() -> Rotations.of(encoder.getAbsPosition()), encoder);
  }
}
