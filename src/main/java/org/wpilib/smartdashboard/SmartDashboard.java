package org.wpilib.smartdashboard;

import org.wpilib.networktables.NetworkTable;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.telemetry.Telemetry;
import org.wpilib.telemetry.TelemetryLoggable;
import org.wpilib.tunable.ComplexTunable;
import org.wpilib.tunable.Tunables;

/**
 * 2027 compatibility bridge for SmartDashboard using WPILib 2027 Telemetry and Tunables backend.
 */
public final class SmartDashboard {
  private static final NetworkTable s_table = NetworkTableInstance.getDefault().getTable("SmartDashboard");

  private SmartDashboard() {}

  public static void putData(String key, Object data) {
    if (data instanceof ComplexTunable tunable) {
      Tunables.publish(key, tunable);
    } else if (data instanceof TelemetryLoggable loggable) {
      Telemetry.log(key, loggable);
    } else {
      Telemetry.log(key, data);
    }
  }

  public static void putNumber(String key, double value) {
    Telemetry.log(key, value);
  }

  public static void putBoolean(String key, boolean value) {
    Telemetry.log(key, value);
  }

  public static void putString(String key, String value) {
    Telemetry.log(key, value);
  }

  public static double getNumber(String key, double defaultValue) {
    return s_table.getEntry(key).getDouble(defaultValue);
  }

  public static boolean getBoolean(String key, boolean defaultValue) {
    return s_table.getEntry(key).getBoolean(defaultValue);
  }

  public static String getString(String key, String defaultValue) {
    return s_table.getEntry(key).getString(defaultValue);
  }

  public static void updateValues() {
    // No-op in 2027
  }
}
