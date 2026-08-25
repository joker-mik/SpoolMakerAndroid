# UltiMaker spool tag format used by this port

The app reproduces the raw NDEF byte stream used by the compatible spool-tag
format. It writes the stream directly to NTAG user page 4 rather than adding an
NFC Forum Type 2 TLV wrapper. The current writer accepts NTAG215 and NTAG216;
the 228-byte write image fits in both user-memory areas.

## Message layout

| Record | TNF | Type | ID | Payload |
|---|---:|---|---|---:|
| Material | External | `ultimaker.nl:material` | `1` | 108 bytes |
| Signature | Well-known | `Sig` | none | 2 bytes (`20 00`) |
| Status | External | `ultimaker.nl:stat` | `2` | 20 bytes |
| Status copy | External | `ultimaker.nl:stat` | `2` | 20 bytes |

All records use the NDEF short-record form. The resulting 226-byte NDEF message
is padded with two zero bytes to a four-byte NTAG page boundary, resulting in a
228-byte write image.

## Material payload (108 bytes)

| Offset | Length | Meaning |
|---:|---:|---|
| 0 | 1 | Format version (`0`) |
| 1 | 1 | Compatibility (`0`) |
| 2 | 14 | Tag UID as uppercase hexadecimal UTF-8, zero padded |
| 16 | 8 | Big-endian IEEE-754 `double`; Unix seconds for SpoolMaker dates, `0.0` when no date is selected |
| 24 | 16 | Material UUID bytes |
| 40 | 2 | Station ID, unsigned big-endian (`0xAFFE` for newly written tags) |
| 42 | 64 | Batch code, UTF-8, zero padded (`123456789AB` for newly written tags) |
| 106 | 1 | SpoolMaker metadata marker (`0x53`, ASCII `S`) on newly written tags |
| 107 | 1 | Date-meaning code: `0` none, `1` manufactured, `2` purchased, `3` opened, `4` created |

Original/factory tags may use different values in bytes 106..107. The decoder
therefore exposes the two trailing bytes as data and only interprets the date
meaning when byte 106 contains the SpoolMaker marker.

## Status payload (20 bytes)

| Offset | Length | Meaning |
|---:|---:|---|
| 0 | 1 | Format version (`0`) |
| 1 | 1 | Compatibility (`0`) |
| 2 | 1 | Unit (`2` = milligrams) |
| 3 | 4 | Total amount, unsigned big-endian |
| 7 | 4 | Remaining amount, unsigned big-endian |
| 11 | 8 | Total usage duration in seconds, unsigned big-endian (new tags start at `0`) |
| 19 | 1 | CRC-8 over bytes 0 through 18 |

CRC-8 uses polynomial `0x07`, initial value `0x00`, no reflection and no final
XOR. The check vector ASCII `123` produces `0xC0`.

## Supported physical tags

Before writing, the Android app identifies the tag with `GET_VERSION`. Only
NTAG215 and NTAG216 are accepted. The app reads lock/configuration information
and rejects a target range that is statically locked, dynamically locked or
password-protected. After writing, only the 228-byte target range is required
for the immediate byte-for-byte verification; full reads use the detected
user-memory size.
