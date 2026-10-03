# Changelog

All notable changes to HyTweaks. Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Sleep percentage ignores AFK and Creative players, so they can't stop the night being skipped. Set how long before a player counts as AFK with `afkMinutes`.
- Durability warning also covers the armor you're wearing and your utility slot.
- Stack refill and tool replace also work for the utility slot, so torches refill and a broken shield is swapped for a working one.
- Items you pick up or craft top up a matching stack in your utility slot first, so collected torches go straight to your off hand.
- Slab placement: crouch to lock the current orientation, so a whole row of slabs goes the same way wherever you aim.

## [1.0.1] - 2026-10-03

### Fixed

- Tool replace no longer loses the replacement when a tool breaks partway through a swing that hits several blocks, such as a sickle sweeping crops. Also covers pickaxes, hatchets, hoes and staffs.

## [1.0.0] - 2026-10-03

First release.

### Added

- Stack refill: a used-up stack in your hand is refilled from your inventory, backpack or hotbar.
- Tool replace: a broken tool or weapon is swapped for a working one of the same kind. The broken one is kept for repair.
- Durability warning: a notification and sound when your held tool drops to 10% durability.
- Slab placement: place slabs by where you aim on a face, and complete a slab into a full block. A translucent box previews the result.
- Map refresh: the world map shows your builds within about 2 seconds.
- Sleep percentage: the night is skipped once half the players are in bed.

[Unreleased]: https://github.com/phntmcc/HyTweaks/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/phntmcc/HyTweaks/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/phntmcc/HyTweaks/releases/tag/v1.0.0
