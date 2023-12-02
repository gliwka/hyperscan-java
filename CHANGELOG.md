# Change Log
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

## [5.4.0-2.0.0] 2021-03-31

### Added
* New PatternFilter allowing for prefiltering of java regex patterns similar to chimera
* Windows support
* Possibility to manually specify expression ids

### Changed
* Moved access to native library from JNA to JavaCPP
* Removed context object from expressions

### Fixed
* Lock contention while scanning with high concurrency ([#89](https://github.com/gliwka/hyperscan-java/issues/89))