# CampusBite — Performance Profiling & LeakCanary Verification Log

## 1. Memory Leak Audit (LeakCanary)
- Integrated LeakCanary 2.14 in `debugImplementation`
- Test Scenarios:
  1. Login -> Browse Menu -> Cart -> Place Order -> Navigate back
  2. Rotate device across Dashboard and Detail screens
  3. Continuous scrolling through 50+ items in menu catalogue
- Result: **0 retained objects, 0 memory leaks detected**

## 2. UI & RecyclerView Rendering
- Enabled ListAdapter with DiffUtil (`CampusAdapter.kt`)
- RecycledViewPool tuning: item cache size set to 20 for food cards
- Glide disk caching: `DiskCacheStrategy.AUTOMATIC` with thumbnail cross-fade

## 3. Network & Battery Profiling
- API requests dispatched exclusively on `Dispatchers.IO`
- Offline caching with Room ensures zero unnecessary network hits in Airplane Mode
