package frc.robot.commands.auto;

import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.util.Units;
import org.wpilib.networktables.DoubleArraySubscriber;
import org.wpilib.networktables.DoubleSubscriber;
import org.wpilib.networktables.DoublePublisher;
import org.wpilib.networktables.StringPublisher;
import org.wpilib.networktables.NetworkTable;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.command2.Commands;
import org.wpilib.command2.SequentialCommandGroup;
import org.wpilib.command2.WaitCommand;

import frc.robot.subsystems.ShooterSubsystem;
import frc.robot.subsystems.swervedrive.SwerveSubsystem;

// (Optional) Use WPILib's official AprilTag field layout instead of a Limelight-only pipeline.
// import org.wpilib.vision.apriltag.AprilTagFieldLayout;
// import org.wpilib.vision.apriltag.AprilTagFields;
// import org.wpilib.math.geometry.Pose3d;

/**
 * Autonomous routine to locate AprilTag ID 4 and shoot.
 *
 * Sequence:
 * 1. Drive forward about 1 foot to improve initial visibility.
 * 2. Approach AprilTag ID 4 with Limelight until the bumper is about 1.5 m away.
 * 3. Stop and hold position.
 * 4. Pause briefly to stabilize aim.
 * 5. Fire using {@link ShooterSubsystem#shoot()}.
 */
public class AutoShootAtTag4 extends SequentialCommandGroup {

    /**
     * Builds the autonomous sequence that drives to and shoots at AprilTag 4.
     *
     * @param drivebase swerve subsystem used for autonomous motion
     * @param shooter shooter subsystem used to fire game pieces
     */
    public AutoShootAtTag4(SwerveSubsystem drivebase, ShooterSubsystem shooter) {
        
        // 1. Initialize NT4 Subscribers ONCE for Limelight reads
        NetworkTable limelight = NetworkTableInstance.getDefault().getTable("limelight");
        
        final DoubleSubscriber tidSub = limelight.getDoubleTopic("tid").subscribe(-1.0);
        final DoubleSubscriber tvSub = limelight.getDoubleTopic("tv").subscribe(0.0);
        final DoubleArraySubscriber poseSub = limelight.getDoubleArrayTopic("targetpose_cameraspace").subscribe(new double[0]);

        // 2. Initialize NT4 Publishers ONCE for dashboard telemetry
        // Putting them in "SmartDashboard" ensures standard driver station apps (Elastic, AdvantageScope) see them instantly
        NetworkTable dashboard = NetworkTableInstance.getDefault().getTable("SmartDashboard");
        
        final StringPublisher statusPub = dashboard.getStringTopic("LL Status").publish();
        final DoublePublisher txPub = dashboard.getDoubleTopic("LL tx (m)").publish();
        final DoublePublisher tyPub = dashboard.getDoubleTopic("LL ty (m)").publish();
        final DoublePublisher tzPub = dashboard.getDoubleTopic("LL tz (m)").publish();
        final DoublePublisher camToTagPub = dashboard.getDoubleTopic("LL camera->tag (m)").publish();
        final DoublePublisher bumperToTagPub = dashboard.getDoubleTopic("LL bumper->tag (m)").publish();

        addCommands(

            // Step 1: Drive forward a short distance (~1 ft) to improve initial tag visibility.
            Commands.run(() -> drivebase.drive(
                        new Translation2d(0.25, 0.0), // Forward velocity of 0.25 m/s.
                        0.0, // No rotation.
                        true // Field-relative.
                    ), drivebase)
                    .withTimeout(Units.feetToMeters(1)) // Run long enough to cover about 1 ft.
                    .andThen(() -> drivebase.drive( // Then stop.
                        new Translation2d(0.0, 0.0),
                        0.0,
                        true
                    )),

            // Step 2: Approach AprilTag 4 using Limelight until bumper distance is about 1.5 m.
            Commands.run(() -> {
                
                // Read from our pre-allocated subscriber
                if (tidSub.get() == 4.0) {
                    System.out.println("tracking id 4");
                    statusPub.set("Tracking ID 4");

                    // Read the target-valid flag ("tv")
                    if (tvSub.get() < 1.0) {
                        statusPub.set("No target");
                        return;
                    }

                    // Validate camera-space pose data (x, y, z).
                    double[] pose = poseSub.get();
                    if (pose == null || pose.length < 3) {
                        statusPub.set("No pose array");
                        return;
                    }

                    double tx = pose[0]; // Horizontal offset (m).
                    double ty = pose[1]; // Vertical offset (m).
                    double tz = pose[2]; // Forward distance from camera to tag (m).

                    // Compute camera-to-tag straight-line distance (Euclidean norm).
                    double cameraToTagDist = Math.sqrt(tx * tx + ty * ty + tz * tz);

                    // Convert camera-to-tag distance into bumper-to-tag distance using camera offset.
                    double camToBumper = 0.3302; // Measure this on your robot (meters).
                    double bumperToTagDist = Math.max(0.0, cameraToTagDist - camToBumper);

                    // Publish camera-space values via Publisher
                    txPub.set(tx);
                    tyPub.set(ty);
                    tzPub.set(tz);
                    camToTagPub.set(cameraToTagDist);
                    bumperToTagPub.set(bumperToTagDist);

                    // Stop threshold (1.5 m from bumper).
                    double stopDistance = 1.5;

                    if (bumperToTagDist >= stopDistance) {
                        System.out.println(bumperToTagDist);
                        drivebase.drive(new Translation2d(0.25, 0.0), 0.0, true);
                    } else {
                        System.out.println(bumperToTagDist);
                        drivebase.drive(new Translation2d(0.0, 0.0), 0.0, true);
                    }
                } else {
                    System.out.println("id not found");
                    statusPub.set("ID 4 not found");
                    drivebase.drive(new Translation2d(0.0, 0.0), 0.0, true);
                }

                /* --- OPTIONAL FIELD LAYOUT VERSION ---
                 * Uncomment this block and comment out the above Limelight chase code
                 * if using WPILib official field layout instead of paper tag.
                 *
                 * AprilTagFieldLayout fieldLayout = AprilTagFields.k2025Crescendo.loadAprilTagLayoutField();
                 * Pose3d tagPose = fieldLayout.getTagPose(4).get();  // ID 4 pose on field
                 * drivebase.driveToPose(tagPose.toPose2d());
                 * turret.trackTargetWithLimelight(); // Align turret once at tag
                 */
            }, drivebase).withTimeout(10.0),

            // Step 3: Stop drivebase fully.
            Commands.runOnce(() -> drivebase.drive(new Translation2d(0.0, 0.0), 0.0, true), drivebase),

            // Step 4: Small pause to stabilize aim before firing.
            new WaitCommand(0.3),

            // Step 5: Shoot at AprilTag 4.
            shooter.shoot()

            // --- OPTIONAL FAILSAFE ---
            // If AprilTag 4 is not found within X seconds, skip to shooting anyway:
            // .deadlineWith(new WaitCommand(3.0).andThen(shooter.TimedOuttake()))
        );
    }
}