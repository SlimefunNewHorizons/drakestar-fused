# 🌐 Networking, LAN Hosting & Single-Instance Guidelines

## ⚠️ The "Double-Hosting" Conflict: Why Mods Like Essential Mod Cause Havoc

When using **DrakeStar Fused**, Minecraft's built-in `IntegratedServer` is directly injected with the **Bukkit/Purpur runtime** and the **Slimefun-Rust SIMD engine**. This effectively transforms the internal singleplayer loop into a sovereign, high-performance Purpur server operating in the same process memory as your client.

### 🚫 The Collision with Tunneling Mods (Essential Mod, e4mc, World Host)

Mods like **Essential Mod**, **e4mc**, or **World Host** hijack Minecraft's vanilla `Publish to LAN` feature by:
1. Wrapping packet dispatch loops in custom UPnP/NAT-punching tunnels.
2. Intercepting authentication packets and replacing player network handlers.
3. Forcing vanilla client packet serializers where Purpur/Bukkit custom payload channels (Plugin Messaging Channels) are expected.

**If a player tries to run Essential Mod on top of DrakeStar Fused:**
* ❌ **Packet Channel Desynchronization:** Custom Slimefun block data (20,000+ items, machine energy states, quantum storage metadata) will fail to deserialize across Essential's proprietary proxy tunnel, corrupting world block entities.
* ❌ **Dual Tick Loops & Thread Collisions:** Essential Mod injects network tick watchers that fight against Bukkit's internal `ServerTickThread` and the off-heap Rust SIMD tickers, causing tick-loop freezing, TPS crashes, and memory leaks.
* ❌ **Player Session Conflicts:** Essential assumes a standard vanilla client session and strips Bukkit player profiles, breaking Slimefun permission checks, research unlocks, and backpack UUIDs.

---

## 🏛️ Supported Topologies: How to Play With Friends

We provide two clean, rock-solid topologies depending on your goals:

### Option A: Clean LAN / Direct IP / Virtual VPN (Recommended for Friends)
If you are playing with friends using the single-instance **DrakeStar Fused** client:
1. In-game, simply click **Open to LAN** (Vanilla Minecraft menu) or configure your listening port in `fused-config.json` (e.g., `25565`).
2. Have your friends connect directly via:
   - **Local Wi-Fi / Ethernet:** `192.168.x.x:25565`
   - **Secure Private Mesh (Tailscale / ZeroTier):** `100.x.y.z:25565`
3. This maintains 100% native packet throughput, zero third-party proxy lag, full Iris/Sodium shader support, and zero risk of world corruption.

---

### Option B: Dedicated Local Server via DrakeStar Launcher (For 24/7 Hosting)
If your goal is to host a persistent server for friends where you don't want your client open all the time, or if you want complex proxy plugins:
1. **Do not use Essential Mod.**
2. In the **DrakeStar Launcher**, select the **Purpur Slimefun LAN** mode.
3. Click **▶ INICIAR SERVIDOR LOCAL + CONSOLA**.
4. The launcher will run an isolated, dedicated **DrakesPurpur** instance with full Blackwall console telemetry, and you can join it from your client via `localhost:25565`.
5. You can forward ports or use Tailscale without interfering with your Minecraft client graphics loop.

---

## 📋 Compatibility Matrix

| Networking Solution | Supported with DrakeStar Fused? | Result / Behavior |
| :--- | :---: | :--- |
| **Vanilla Open to LAN** | ✅ **YES** | Native socket binding, full Slimefun & StarSuites support. |
| **Tailscale / WireGuard** | ✅ **YES (Recommended)** | Zero-config, encrypted peer-to-peer LAN across the Internet. |
| **Direct Port Forwarding** | ✅ **YES** | Forward TCP port 25565 to your local IP. |
| **DrakeStar Dedicated LAN** | ✅ **YES** | Separate headless launcher profile with live Blackwall console. |
| **Essential Mod** | ❌ **PROHIBITED** | Causes socket hijack, packet drops, and Slimefun NBT corruption. |
| **e4mc / World Host** | ⚠️ **NOT RECOMMENDED** | Risk of packet truncation with custom items >20k. |

