package frc.robot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import swervelib.commands2.SwerveParser;

public class SwerveParserTest {
  @Test
  public void testParseSwerveDirectory() {
    File deployDir = new File("src/main/deploy/swerve");
    assertTrue(deployDir.exists(), "deploy/swerve directory must exist");

    assertDoesNotThrow(() -> {
      SwerveParser.parse(deployDir);
    });

    assertNotNull(SwerveParser.swerveDriveJson);
    assertEquals(4, SwerveParser.swerveDriveJson.modules.length);
    assertEquals("pigeon2_can", SwerveParser.swerveDriveJson.gyro.type);

    assertNotNull(SwerveParser.physicalPropertiesJson);
    assertEquals(5.2734375, SwerveParser.physicalPropertiesJson.gearing.drive.gearRatio, 1e-4);
    assertEquals(26.091, SwerveParser.physicalPropertiesJson.gearing.angle.gearRatio, 1e-4);

    assertNotNull(SwerveParser.pidfPropertiesJson);
    assertEquals(1.0, SwerveParser.pidfPropertiesJson.drive.p);
    assertEquals(50.0, SwerveParser.pidfPropertiesJson.angle.p);

    assertNotNull(SwerveParser.moduleJsons);
    assertEquals(4, SwerveParser.moduleJsons.length);
    for (var module : SwerveParser.moduleJsons) {
      assertNotNull(module);
      assertEquals("talonfx_krakenx60", module.drive.type);
      assertEquals("talonfx_krakenx44", module.angle.type);
      assertEquals("cancoder_can", module.absoluteEncoder.type);
    }
  }
}
