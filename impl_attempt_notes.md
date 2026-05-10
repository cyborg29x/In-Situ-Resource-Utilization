This document describes implementation attempts that ran into difficulties due to various issues.

- Volatiles to Fuel ability tooltip line cannot be made to display full precision because game API returns integer values for getCommodityQuantity and similar functions. Has to stay integer input.
- Metals and Transplutonics to Supplies have special conditions that prevent it from displaying if they're consumed within a frame to prevent the tooltip from rapidly flickering between states each frame.