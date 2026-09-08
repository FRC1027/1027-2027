package swervelib.parser.json;

import static org.wpilib.units.Units.Rotations;

import io.avaje.jsonb.Json;
import org.wpilib.util.Pair;
import org.wpilib.math.system.DCMotor;
import org.wpilib.units.measure.Angle;
import org.wpilib.hardware.rotation.AnalogEncoder;
import org.wpilib.hardware.rotation.DutyCycleEncoder;
import java.util.function.Supplier;
import swervelib.parser.deserializer.ReflectionsManager.AbsoluteEncoder;
import swervelib.parser.deserializer.ReflectionsManager.Gyro;
import swervelib.parser.deserializer.ReflectionsManager.VendorMotorController;
import swervelib.parser.json.SwerveDriveJson.GyroAxis;
import yams.motorcontrollers.SmartMotorController;
import yams.motorcontrollers.SmartMotorControllerConfig;

/**
 * Device JSON parsed class. Used to access the JSON data.
 */
@Json
public class DeviceJson
{

  /**
   * The device type
   */
  public String type;
  /**
   * The CAN ID or pin ID of the device.
   */
  public int    id;
  /**
   * SmartIO Channel.
   */
  public int    channel = 0;
  /**
   * The CAN bus name which the device resides on if using CAN.
   */
  public String canbus  = "";

  /**
   * Get the DC motor from the motor type.
   *
   * @param motorType Motor type.
   * @return {@link DCMotor}
   */
  public static DCMotor getDCMotor(String motorType)
  {
    switch (motorType)
    {
      case "neo2":
      case "neo":
        return DCMotor.getNEO(1);
      case "neo550":
        return DCMotor.getNeo550(1);
      case "vortex":
        return DCMotor.getNeoVortex(1);
      case "minion":
        return DCMotor.getMinion(1);
      case "krakenx44":
        return DCMotor.getKrakenX44(1);
      case "krakenx60":
        return DCMotor.getKrakenX60(1);
      case "pulsar":
        return new DCMotor(12, 3.1, 189, 1, 7500, 1);
      default:
        throw new IllegalArgumentException("Invalid motor type: " + motorType);
    }
  }

  /**
   * Get the gyro angle supplier.
   *
   * @param axis     Gyro axis.
   * @param inverted Invert the gyro angle.
   * @return {@link Supplier} of {@link Angle}, or {@code null} if the gyro type is {@code "custom"} and the
   * user is expected to configure the gyro themselves.
   */
  public Pair<Supplier<Angle>, Object> getGyro(GyroAxis axis, boolean inverted)
  {
    if ("custom".equalsIgnoreCase(type))
    {
      return null;
    }
    String vendorType;
    String vendorConnectionType;
    if (type.contains("_"))
    {
      String[] vendorData           = type.split("_");
      vendorType           = vendorData[0].toLowerCase();
      vendorConnectionType = vendorData[1].toLowerCase();
    }
    else
    {
      vendorType           = type.toLowerCase();
      vendorConnectionType = "can";
    }
    switch (vendorConnectionType)
    {
      case "can":
        String safeBus = (canbus == null) ? "" : canbus;
        switch (vendorType)
        {
          case "navx":
          case "navx3":
            return Gyro.NAVX3.getGyro(id, safeBus, axis, inverted);
          case "pigeon2":
            return Gyro.PIGEON2.getGyro(id, safeBus, axis, inverted);
          case "canandgyro":
            return Gyro.CANANDGYRO.getGyro(id, safeBus, axis, inverted);
        }
      case "internal":
        throw new IllegalArgumentException("Internal gyro not supported yet!");
    }
    throw new IllegalArgumentException("Invalid gyro type: " + type);
  }

  /**
   * Vendor of a device (motor controller, encoder, or gyroscope).
   */
  public enum VENDOR
  {
    /**
     * Cross The Road Electronics.
     */
    CTRE,
    /**
     * REV Robotics.
     */
    REV,
    /**
     * ThriftyBot.
     */
    THRIFTYBOT,
    /**
     * AndyMark.
     */
    ANDYMARK,
    /**
     * Redux Robotics.
     */
    REDUX,
    /**
     * Studica.
     */
    STUDICA,
    /**
     * Device attached to the roboRIO SmartIO port (DIO/Analog).
     */
    SMARTIO,
    /**
     * Limelight.
     */
    LIMELIGHT,
    /**
     * Vendor could not be determined.
     */
    UNKNOWN
  }

  /**
   * Get the vendor of the device.
   *
   * @param attachedType Vendor to return when the device is directly attached (not on CAN/DIO/Analog),
   *                      since the attached vendor cannot be determined from the device type string alone.
   * @return Vendor of the device.
   */
  public VENDOR getVendor(VENDOR attachedType)
  {
    String vendorType;
    String vendorConnectionType;
    if (type.contains("_"))
    {
      String[] vendorData           = type.split("_");
      vendorType           = vendorData[0].toLowerCase();
      vendorConnectionType = vendorData[1].toLowerCase();
    }
    else
    {
      vendorType           = type.toLowerCase();
      vendorConnectionType = "can";
    }
    switch (vendorType)
    {
      case "systemcore":
        return VENDOR.LIMELIGHT;
      case "navx":
      case "navx3":
        return VENDOR.STUDICA;
      case "talonfx":
      case "talonfxs":
      case "cancoder":
      case "pigeon2":
      case "krakenx60":
      case "krakenx44":
      case "falcon500":
      case "minion":
        return VENDOR.CTRE;
      case "sparkmax":
      case "sparkflex":
      case "neo":
      case "neo2":
      case "neo550":
      case "vortex":
        return VENDOR.REV;
      case "revthroughbore":
        switch (vendorConnectionType)
        {
          case "attached": return attachedType;
          case "dio": return VENDOR.SMARTIO;
        }
      case "nova":
      case "pulsar":
        return VENDOR.THRIFTYBOT;
      case "andymarkhexbore":
        switch (vendorConnectionType)
        {
          case "attached": return attachedType;
          case "dio":
          case "analog": return VENDOR.SMARTIO;
          case "can": return VENDOR.ANDYMARK;
        }
      case "canandgyro": return VENDOR.REDUX;
      case "canandmag":
        switch (vendorConnectionType)
        {
          case "attached": return attachedType;
          case "dio": return VENDOR.SMARTIO;
          case "can": return VENDOR.REDUX;
        }
      case "srxmag":
        switch (vendorConnectionType)
        {
          case "attached": return attachedType;
          case "analog": return VENDOR.SMARTIO;
        }
      case "thrifty":
        switch (vendorConnectionType)
        {
          case "attached": return attachedType;
          case "analog": return VENDOR.SMARTIO;
        }
    }
    return VENDOR.UNKNOWN;
  }

  /**
   * Get the {@link VendorMotorController} object for the device.
   *
   * @return {@link VendorMotorController} object for the device.
   */
  public VendorMotorController getMotorController()
  {
    String vendorType;
    if (type.contains("_"))
    {
      String[] vendorData = type.split("_");
      vendorType           = vendorData[0].toLowerCase();
    }
    else
    {
      vendorType = type.toLowerCase();
    }
    switch (vendorType)
    {
      case "nova":
      case "pulsar":
        return VendorMotorController.NOVA;
      case "sparkmax":
      case "neo":
      case "neo2":
      case "neo550":
        return VendorMotorController.SPARKMAX;
      case "sparkflex":
      case "vortex":
        return VendorMotorController.SPARKFLEX;
      case "talonfx":
      case "krakenx60":
      case "krakenx44":
      case "falcon500":
        return VendorMotorController.TALONFX;
      case "talonfxs":
      case "minion":
        return VendorMotorController.TALONFXS;
    }
    return VendorMotorController.NONE;
  }

  /**
   * Get the Absolute Encoder Supplier and Vendor Absolute Encoder Object.
   *
   * @param angleMotorVendor     Vendor of the angle/steering/azimuth motor controller, used when the
   *                             absolute encoder is attached to the angle motor controller.
   * @param angleMotorController Angle/steering/azimuth {@link SmartMotorController}, used when the
   *                             absolute encoder is attached to the angle motor controller.
   * @param inverted             Inversion of the absolute encoder.
   * @return Pair of {@link Supplier} and Vendor Absolute Encoder {@link Object}
   */
  public Pair<Supplier<Angle>, Object> getAbsoluteEncoder(VendorMotorController angleMotorVendor,
                                                          SmartMotorController angleMotorController, boolean inverted)
  {
    String vendorType;
    String vendorConnectionType;
    if (type.contains("_"))
    {
      String[] vendorData = type.split("_");
      vendorType           = vendorData[0].toLowerCase();
      vendorConnectionType = vendorData[1].toLowerCase();
    }
    else
    {
      vendorType = type.toLowerCase();
      switch (vendorType)
      {
        case "cancoder":
        case "canandmag":
        case "andymarkhexbore":
        case "splineencoder":
          vendorConnectionType = "can";
          break;
        case "analog":
          vendorConnectionType = "analog";
          break;
        case "dutycycle":
          vendorConnectionType = "dio";
          break;
        default:
          vendorConnectionType = "can";
          break;
      }
    }
    switch (vendorConnectionType)
    {
      case "analog":
      {
        var analogEncoder = new AnalogEncoder(id);
        analogEncoder.setInverted(inverted);
        return Pair.of(() -> Rotations.of(analogEncoder.get()), analogEncoder);
      }
      case "dio":
      {
        var dutyCycleEncoder = new DutyCycleEncoder(id);
        dutyCycleEncoder.setInverted(inverted);
        return Pair.of(() -> Rotations.of(dutyCycleEncoder.get()), dutyCycleEncoder);
      }
      case "attached":
        switch (vendorType)
        {
          case "andymarkhexbore":
          case "canandmag":
          case "revthroughbore":
          case "srxmag":
          case "dutycycle":
            return angleMotorVendor.getAbsoluteEncoder("dutycycle", angleMotorController, inverted);
          case "analog":
            return angleMotorVendor.getAbsoluteEncoder("analog", angleMotorController, inverted);
          case "analog5v":
            return angleMotorVendor.getAbsoluteEncoder("analog5v", angleMotorController, inverted);
          default: throw new IllegalArgumentException("Invalid encoder type: " + vendorType);
        }
      case "can":
        String safeBus = (canbus == null) ? "" : canbus;
        switch (vendorType)
        {
          case "cancoder": return AbsoluteEncoder.CANCODER.getAbsoluteEncoder(id, safeBus, inverted);
          case "canandmag": return AbsoluteEncoder.CANANDMAG.getAbsoluteEncoder(id, safeBus, inverted);
          case "andymarkhexbore": return AbsoluteEncoder.ANDYMARK.getAbsoluteEncoder(id, safeBus, inverted);
          case "splineencoder": return AbsoluteEncoder.SPLINE_ENCODER.getAbsoluteEncoder(id, safeBus, inverted);
          default: throw new IllegalArgumentException("Invalid encoder type: " + vendorType);
        }
      default: throw new IllegalArgumentException("Invalid encoder connection type: " + vendorConnectionType);
    }
  }

  /**
   * Get the {@link SmartMotorController} from the {@link DeviceJson} when given the
   * {@link SmartMotorControllerConfig}.
   *
   * @param config {@link SmartMotorControllerConfig} to apply when creating {@link SmartMotorController}.
   * @return {@link SmartMotorController}
   */
  public SmartMotorController getSmartMotorController(SmartMotorControllerConfig config)
  {
    String motorControllerType;
    String motorType;
    if (type.contains("_"))
    {
      String[] subtypes    = type.split("_");
      motorControllerType = subtypes[0].toLowerCase();
      motorType           = subtypes[1].toLowerCase();
    }
    else
    {
      String lower = type.toLowerCase();
      switch (lower)
      {
        case "krakenx60":
        case "krakenx44":
          motorControllerType = "talonfx";
          motorType           = lower;
          break;
        case "neo":
        case "neo2":
        case "neo550":
          motorControllerType = "sparkmax";
          motorType           = lower;
          break;
        case "vortex":
          motorControllerType = "sparkflex";
          motorType           = lower;
          break;
        case "minion":
          motorControllerType = "talonfxs";
          motorType           = lower;
          break;
        case "pulsar":
          motorControllerType = "nova";
          motorType           = lower;
          break;
        case "talonfx":
          motorControllerType = "talonfx";
          motorType           = "krakenx60";
          break;
        case "talonfxs":
          motorControllerType = "talonfxs";
          motorType           = "minion";
          break;
        case "sparkmax":
          motorControllerType = "sparkmax";
          motorType           = "neo";
          break;
        case "sparkflex":
          motorControllerType = "sparkflex";
          motorType           = "vortex";
          break;
        case "nova":
          motorControllerType = "nova";
          motorType           = "pulsar";
          break;
        default:
          throw new IllegalArgumentException("Invalid motor controller / motor type: " + type);
      }
    }
    DCMotor  motor      = getDCMotor(motorType);
    String   safeCanbus = (canbus == null) ? "" : canbus;
    switch (motorControllerType)
    {
      case "talonfx":
        return VendorMotorController.TALONFX.getMotorController(id, safeCanbus, config, motor);
      case "talonfxs":
        return VendorMotorController.TALONFXS.getMotorController(id, safeCanbus, config, motor);
      case "sparkmax":
        return VendorMotorController.SPARKMAX.getMotorController(id, safeCanbus, config, motor);
      case "sparkflex":
        return VendorMotorController.SPARKFLEX.getMotorController(id, safeCanbus, config, motor);
      case "nova":
        return VendorMotorController.NOVA.getMotorController(id, safeCanbus, config, motor);
      default:
        throw new IllegalArgumentException("Invalid motor controller type: " + motorControllerType);
    }
  }

}
