# Bugs and Playtesting Notes

This file includes any bugs I find and observations I make while playtesting.

## 0.1.26

### Bugs

#### Fixed

- Volatiles to fuel does not show up in the ability tooltip.
- Metal to supplies line not being displayed after I scavenged ~30 units off a derelict. Metal still consumed as expected. Determined that it's because the special line anti-flicker code uses daily capacity instead of per-frame.
- Didn't reserve 7080 units of delivery type contract. Reverted a bugfix attempt and now reserves properly.

#### Pending

- Didn't reserve 330 units of organics after accepting procurement type contract.
- Dne unit of metal keeps appearing in cargo every now and then, it is immediately consumed upon unpausing.

### Notes

- Makes game easier overall, opens more venues of sustaining fleet and acquiring supplies on a budget because more commodities are at discount. Also saves cargo space. No meaningful downsides.
- It just quietly works in the background. Intentional. Conversion of resources more hands-free than I expected, doesn't really matter how they end up in the end, be it ore, metal or supply.
- Oddly incentivizes me away from using transverse jump ability because extra travel time to jump points is spent converting and jump needs supplies to recover from.
- I find myself wishing it wouldn't convert volatiles to fuel when I find them on sale somewhere. I have plenty of fuel (10k units and 20k max) and don't need any more at the moment.