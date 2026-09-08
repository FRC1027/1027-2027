package swervelib.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import org.junit.jupiter.api.Test;
import frc.robot.util.Elastic;

class SwerveParserTest {

  @Test
  void testParseSwerveDirectory() {
    File deployDirectory = new File("src/main/deploy/swerve");
    assertTrue(deployDirectory.exists(), "Deploy directory must exist");

    SwerveParser parser = SwerveParser.parse(deployDirectory);
    assertNotNull(parser, "Parsed SwerveParser instance must not be null");
    assertNotNull(SwerveParser.swerveDriveJson, "swerveDriveJson must not be null");
    assertNotNull(SwerveParser.pidfPropertiesJson, "pidfPropertiesJson must not be null");
    assertNotNull(SwerveParser.physicalPropertiesJson, "physicalPropertiesJson must not be null");
    assertNotNull(SwerveParser.moduleJsons, "moduleJsons array must not be null");
    assertEquals(4, SwerveParser.moduleJsons.length, "Should parse 4 swerve modules");
  }

  @Test
  void testElasticNotificationSerialization() {
    assertDoesNotThrow(() -> {
      Elastic.Notification notification = new Elastic.Notification(
          Elastic.NotificationLevel.INFO,
          "Test Title",
          "Test Description"
      );
      // Ensure Elastic static serialization does not throw IllegalArgumentException
      Elastic.sendNotification(notification);
    });
  }

  @Test
  void testSwerveSubsystemInitialization() {
    org.wpilib.hardware.hal.HAL.initialize();
    File deployDirectory = new File("src/main/deploy/swerve");
    assertDoesNotThrow(() -> {
      frc.robot.subsystems.swervedrive.SwerveSubsystem swerve = 
          new frc.robot.subsystems.swervedrive.SwerveSubsystem(deployDirectory);
      assertNotNull(swerve);
    });
  }
}
