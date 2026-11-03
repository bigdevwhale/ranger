package app.ranger

import app.ranger.data.Digest
import app.ranger.data.Fit
import app.ranger.data.Formats
import app.ranger.data.Geo
import app.ranger.data.Strava
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class FormatsTest {
    private val t0 = Instant.parse("2026-05-02T06:00:00Z").toEpochMilli()

    /** A straight run north at 3 m/s with a sample every 5 s and a heart rate. */
    private fun gpx(n: Int = 400): String {
        val sb = StringBuilder("""<?xml version="1.0"?><gpx xmlns="http://www.topografix.com/GPX/1/1" xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1"><trk><name>Morning Run</name><type>running</type><trkseg>""")
        for (i in 0 until n) {
            val lat = 50.0 + i * 15.0 / 111_195
            sb.append("""<trkpt lat="$lat" lon="14.4"><ele>${200 + (i % 50)}</ele><time>${Instant.ofEpochMilli(t0 + i * 5000L)}</time><extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>${140 + i % 10}</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>""")
        }
        return sb.append("</trkseg></trk></gpx>").toString()
    }

    @Test fun gpxWithHeartRate() {
        val p = Formats.parse("run.gpx", gpx().toByteArray())!!
        assertEquals(400, p.points.size)
        assertEquals("Morning Run", p.title)
        assertEquals(140, p.points[0].hr)
        val d = Digest.of(p).run
        assertEquals(5985.0, d.distanceM, 30.0)
        assertTrue(d.hasTrack)
        assertTrue(d.hrHist.isNotEmpty())
        assertEquals(144.0, d.avgHr!!.toDouble(), 1.0)
        // 1 km at 3 m/s is 333 s, 5 km 1667 s
        assertEquals(333.0, d.bestOf(0).toDouble(), 2.0)
        assertEquals(1667.0, d.bestOf(1).toDouble(), 5.0)
        assertEquals(0, d.bestOf(2))
        assertTrue(d.ascentM > 10)
    }

    @Test fun cyclingIsSkipped() {
        assertNull(Formats.parse("ride.gpx", gpx().replace("<type>running</type>", "<type>cycling</type>").toByteArray()))
    }

    @Test fun tcxWithDistance() {
        val sb = StringBuilder("""   <?xml version="1.0" encoding="UTF-8"?><TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"><Activities><Activity Sport="Running"><Lap><TotalTimeSeconds>600</TotalTimeSeconds><DistanceMeters>2000</DistanceMeters><Track>""")
        for (i in 0..120) sb.append("""<Trackpoint><Time>${Instant.ofEpochMilli(t0 + i * 5000L)}</Time><DistanceMeters>${i * 16.5}</DistanceMeters><HeartRateBpm><Value>150</Value></HeartRateBpm></Trackpoint>""")
        sb.append("</Track></Lap></Activity></Activities></TrainingCenterDatabase>")
        val p = Formats.parse("run.tcx", sb.toString().toByteArray())!!
        assertTrue(p.treadmill)
        val r = Digest.of(p).run
        assertEquals(1980.0, r.distanceM, 1.0)
        assertEquals(150, r.avgHr)
        assertTrue(!r.hasTrack)
    }

    /** Writes a minimal FIT file: file_id, records with position, heart rate, distance, and a running session. */
    private fun fit(sport: Int = 1): ByteArray {
        val body = ByteArrayOutputStream()
        fun le(n: Int, v: Long) { val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array(); body.write(b, 0, n) }
        // definition 0: record (20): timestamp u32, lat s32, lon s32, hr u8, distance u32
        body.write(0x40); body.write(0); body.write(0); le(2, 20); body.write(5)
        for ((num, size, type) in listOf(Triple(253, 4, 0x86), Triple(0, 4, 0x85), Triple(1, 4, 0x85), Triple(3, 1, 0x02), Triple(5, 4, 0x86))) { body.write(num); body.write(size); body.write(type) }
        val ts0 = t0 / 1000 - 631_065_600L
        for (i in 0 until 200) {
            body.write(0x00)
            le(4, ts0 + i * 3)
            le(4, ((50.0 + i * 9.0 / 111_195) * 2147483648.0 / 180).toLong())
            le(4, (14.4 * 2147483648.0 / 180).toLong())
            body.write(150 + i % 5)
            le(4, (i * 900).toLong()) // cm
        }
        // definition 1: session (18): sport enum, total_elapsed u32 (ms), total_distance u32 (cm)
        body.write(0x41); body.write(0); body.write(0); le(2, 18); body.write(3)
        for ((num, size, type) in listOf(Triple(5, 1, 0x00), Triple(7, 4, 0x86), Triple(9, 4, 0x86))) { body.write(num); body.write(size); body.write(type) }
        body.write(0x01); body.write(sport); le(4, 600_000); le(4, 179_100)
        val data = body.toByteArray()
        val head = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).put(14).put(0x20).putShort(2100).putInt(data.size).put(".FIT".toByteArray()).putShort(0).array()
        return head + data + byteArrayOf(0, 0)
    }

    @Test fun fitRecords() {
        val b = fit()
        assertTrue(Fit.looksLike(b))
        val p = Formats.parse("x.fit", b)!!
        assertEquals(200, p.points.size)
        assertEquals(50.0, p.points[0].lat!!, 1e-6)
        assertEquals(150, p.points[0].hr)
        assertEquals(1791.0, p.distanceM!!, .1)
        assertEquals(600, p.durationS)
        assertEquals(t0, p.start)
        val r = Digest.of(p).run
        assertEquals(1791.0, r.distanceM, .1)
        assertTrue(r.hasTrack)
    }

    @Test fun fitBikeIsSkipped() = assertNull(Formats.parse("x.fit", fit(sport = 2)))

    @Test fun stravaTable() {
        val csv = "Activity ID,Activity Date,Activity Name,Activity Type,Activity Description,Elapsed Time,Distance,Max Heart Rate,Relative Effort,Commute,Activity Private Note,Activity Gear,Filename,Athlete Weight,Bike Weight,Elapsed Time,Moving Time,Distance\n" +
            "101,\"Mar 5, 2024, 6:12:33 AM\",\"Morning, cold\",Run,,1800,\"5.02\",171,,false,,Clifton 9,activities/101.fit.gz,,,1800,1750,5020.5\n" +
            "102,\"Mar 6, 2024, 6:12:33 AM\",Ride,Ride,,3600,20.0,,,false,,,activities/102.gpx,,,3600,3500,20000\n"
        val rows = Strava.rows(csv)
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals("Morning, cold", r.name)
        assertEquals(5020.5, r.distanceM!!, .01)
        assertEquals("Clifton 9", r.gear)
        assertEquals(Instant.parse("2024-03-05T06:12:33Z").toEpochMilli(), r.start)
        assertEquals(1750, r.moving)
    }

    @Test fun polyline() {
        val pts = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Geo.encode(pts))
        val back = Geo.decode(Geo.encode(pts))
        assertEquals(43.252, back[2].first, 1e-5)
        assertNotNull(back)
    }
}
