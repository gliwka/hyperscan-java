# Change Log
## [5.4.11-3.1.0] 2025-04-06

### Added
* New callback API for efficient custom match processing ([#58](https://github.com/gliwka/hyperscan-java/issues/58))
* Byte-based scanning API for direct operation on byte[] and ByteBuffer without String overhead
* New `hasMatch` methods for quick existence checking that terminate immediately on first match ([#68](https://github.com/gliwka/hyperscan-java/issues/68))

### Performance
* Reduced memory usage for UTF-8 string mapping by dynamically selecting optimal array type

### Fixed
* Fixed upstream vectorscan correctness regression on x86 architecture with targeted patch ([#228](https://github.com/gliwka/hyperscan-java/issues/228), [#231](https://github.com/gliwka/hyperscan-java/issues/231))
* Ensured all native handles are properly reclaimed after database compilation ([#230](https://github.com/gliwka/hyperscan-java/issues/230))
* Reworked UTF-8 position mapping to handle the mapping correctly in edge cases ([#170](https://github.com/gliwka/hyperscan-java/issues/170))

### Changed
* Removed 255 thread limit for concurrent scanning operations ([#222](https://github.com/gliwka/hyperscan-java/issues/222), [#229](https://github.com/gliwka/hyperscan-java/issues/229))


## [5.4.11-3.0.0] 2023-12-03

Due to [missing arm64 supoort amd proprietary licensing of new releases of hyperscan](https://github.com/intel/hyperscan/issues/421), this release of hyperscan-java transitions from hyperscan to the [vectorscan](https://github.com/VectorCamp/vectorscan) fork.

### Breaking
* Windows support has been dropped due to vectorscan not supporting it

### Added
* Support for ARM64 architecture on Linux and macOS (M1/M2/M3 family of chips)

### Fixed
* Database instances not reclaimable by GC ([#161](https://github.com/gliwka/hyperscan-java/issues/161)) - thanks [@mmimica](https://github.com/mmimica)!
* Race condition during tracking of native references on multiple threads ([#158](https://github.com/gliwka/hyperscan-java/issues/158)) - thanks [@mmimica](https://github.com/mmimica)!
* Expression IDs now can have arbitrary space between them without consuming additional memory ([#163](https://github.com/gliwka/hyperscan-java/issues/163)) - thanks [@mmimica](https://github.com/mmimica)!
* Removed superflous duplicate call during mapping of expressions in PatternFilter ([#205](https://github.com/gliwka/hyperscan-java/pull/205)) - thanks [@Jiar](https://github.com/Jiar)!