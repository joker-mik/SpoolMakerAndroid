The NFC tag format and compatibility behavior have been documented through
reverse engineering of original UltiMaker tags and printer firmware.
See [Technical Research](#technical-research--reverse-engineering) for details.

## Technical Research / Reverse Engineering

SpoolMakerAndroid is based on technical analysis and reverse engineering of original UltiMaker NFC spool tags and the selected UltiMaker printer firmware versions.

A detailed technical report is available here (in German):

**[UltiMaker NFC Spool Tags – Technical Reverse Engineering Report (PDF)](docs/research/Ultimaker_NFC_Spulentags_Technische_Zusammenfassung.pdf)**

The report documents, among other things:

* the NTAG216 memory layout used by original UltiMaker spool tags
* the four-record NDEF structure consisting of the material record, `Sig` marker and two status records
* byte-level layouts of the material and status records
* CRC-8 calculation used by the status records
* how the printer determines the current status record using the accumulated usage duration
* observed behavior of the redundant/alternating status records
* analysis of the 8-byte time field
* SpoolMakerAndroid's reader and writer conventions
* compatibility limitations and currently open questions

The findings are based on analysis of original NFC tags, UltiMaker S5 firmware 9.0.3-R1, S8 `libPalantir` from firmware 11.2.4, and the SpoolMakerAndroid implementation.

> **Note:** This is independent reverse-engineering documentation and not an official UltiMaker specification. Compatibility has only been evaluated against the firmware versions and tag data described in the report. Future firmware versions may behave differently.
