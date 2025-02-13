// This file was automatically generated from serializers.md by Knit tool. Do not edit.
package example.exampleSerializer18

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.descriptors.*

import java.util.Date
import java.util.TimeZone
import java.text.SimpleDateFormat
  
object DateAsLongSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("my.app.DateAsLong", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeLong(value.time)
    override fun deserialize(decoder: Decoder): Date = Date(decoder.decodeLong())
}

object DateAsSimpleTextSerializer: KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("my.app.DateAsSimpleText", PrimitiveKind.LONG)
    private val format = SimpleDateFormat("yyyy-MM-dd").apply {
        // Here we explicitly set time zone to UTC so output for this sample remains locale-independent.
        // Depending on your needs, you may have to adjust or remove this line.
        setTimeZone(TimeZone.getTimeZone("UTC"))
    }
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeString(format.format(value))
    override fun deserialize(decoder: Decoder): Date = format.parse(decoder.decodeString())
}

typealias DateAsLong = @Serializable(DateAsLongSerializer::class) Date

typealias DateAsText = @Serializable(DateAsSimpleTextSerializer::class) Date

@Serializable          
class ProgrammingLanguage(val stableReleaseDate: DateAsText, val lastReleaseTimestamp: DateAsLong)

fun main() {
    val format = SimpleDateFormat("yyyy-MM-ddX")
    val data = ProgrammingLanguage(format.parse("2016-02-15+00"), format.parse("2022-07-07+00"))
    println(Json.encodeToString(data))
}
