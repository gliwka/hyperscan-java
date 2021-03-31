# Change Log
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