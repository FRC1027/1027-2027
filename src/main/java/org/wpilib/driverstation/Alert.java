package org.wpilib.driverstation;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compatibility bridge for org.wpilib.driverstation.Alert used by PathplannerLib
 * delegating to org.wpilib.util.Alert in WPILib 2027.
 */
public class Alert implements AutoCloseable {

  public enum Level {
    HIGH(org.wpilib.util.Alert.Level.HIGH),
    MEDIUM(org.wpilib.util.Alert.Level.MEDIUM),
    LOW(org.wpilib.util.Alert.Level.LOW);

    private final org.wpilib.util.Alert.Level m_utilLevel;

    Level(org.wpilib.util.Alert.Level utilLevel) {
      m_utilLevel = utilLevel;
    }

    public org.wpilib.util.Alert.Level getUtilLevel() {
      return m_utilLevel;
    }
  }

  private static final AtomicInteger idCounter = new AtomicInteger(1);
  private final org.wpilib.util.Alert m_alert;

  public Alert(String group, String text, Level level) {
    this(group, "PathPlannerAlert_" + idCounter.getAndIncrement(), text, level);
  }

  public Alert(String text, Level level) {
    this("Alerts", "PathPlannerAlert_" + idCounter.getAndIncrement(), text, level);
  }

  public Alert(String group, String id, String text, Level level) {
    m_alert = new org.wpilib.util.Alert(group, id, text, level.getUtilLevel());
  }

  public void set(boolean active) {
    m_alert.set(active);
  }

  public boolean get() {
    return m_alert.get();
  }

  public void setText(String text) {
    m_alert.setText(text);
  }

  public String getText() {
    return m_alert.getText();
  }

  @Override
  public void close() {
    m_alert.close();
  }
}
