# Ultimaker spool tag format used by this port

The app intentionally reproduces the raw NDEF byte stream written by the
original Spool Maker implementation. It writes the stream directly to NTAG
user page 4 rather than adding an NFC Forum Type 2 TLV wrapper.

## Message layout

| Record | TNF | Type | ID | Payload |
|---|---:|---|---|---:|
| Material | External | `ultimaker.nl:material` | `1` | 108 bytes |
| Signature | Well-known | `Sig` | none | 2 bytes (`20 00`) |
| Status | External | `ultimaker.nl:stat` | `2` | 20 bytes |
| Status copy | External | `ultimaker.nl:stat` | `2` | 20 bytes |

All records use the NDEF short-record form. The resulting 226-byte NDEF
message is padded with two zero bytes to a four-byte NTAG page boundary.

## Material payload (108 bytes)

| Offset | Length | Meaning |
|---:|---:|---|
| 0 | 1 | Format version (`0`) |
| 1 | 1 | Compatibility (`0`) |
| 2 | 14 | Tag UID as uppercase hexadecimal UTF-8, zero padded |
| 16 | 8 | Manufacturing timestamp (Unix seconds), unsigned big-endian (default `0`) |
| 24 | 16 | Material UUID bytes |
| 40 | 2 | Station ID, unsigned big-endian (`0xAFFE`) |
| 42 | 64 | Batch code, UTF-8, zero padded (`123456789AB`) |
| 106 | 2 | Reserved zero bytes |

## Status payload (20 bytes)

| Offset | Length | Meaning |
|---:|---:|---|
| 0 | 1 | Format version (`0`) |
| 1 | 1 | Compatibility (`0`) |
| 2 | 1 | Unit (`2` = milligrams) |
| 3 | 4 | Total amount, unsigned big-endian |
| 7 | 4 | Remaining amount, unsigned big-endian |
| 11 | 8 | Total usage duration in seconds, unsigned big-endian (default `0`) |
| 19 | 1 | CRC-8 over bytes 0 through 18 |

CRC-8 uses polynomial `0x07`, initial value `0x00`, no reflection and no
final XOR. The check vector ASCII `123` produces `0xC0`.
