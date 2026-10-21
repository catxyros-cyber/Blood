/**
 * Trimmed but structurally faithful copies of real Mojang/Fabric metadata
 * (same field names and nesting as piston-meta and meta.fabricmc.net).
 */

import type { AssetIndex, JavaManifest, JavaRuntimeIndex, VersionJson, VersionManifest } from "../mojang/types";

export const manifest: VersionManifest = {
  latest: { release: "1.21.1", snapshot: "24w33a" },
  versions: [
    {
      id: "1.21.1",
      type: "release",
      url: "https://piston-meta.mojang.com/v1/packages/aaaa/1.21.1.json",
      time: "2024-08-08T12:24:45+00:00",
      releaseTime: "2024-08-08T12:24:45+00:00",
      sha1: "aaaa000000000000000000000000000000000001",
    },
    {
      id: "1.12.2",
      type: "release",
      url: "https://piston-meta.mojang.com/v1/packages/bbbb/1.12.2.json",
      time: "2017-09-18T08:39:46+00:00",
      releaseTime: "2017-09-18T08:39:46+00:00",
      sha1: "bbbb000000000000000000000000000000000002",
    },
  ],
};

export const v1211: VersionJson = {
  id: "1.21.1",
  type: "release",
  mainClass: "net.minecraft.client.main.Main",
  releaseTime: "2024-08-08T12:24:45+00:00",
  javaVersion: { component: "java-runtime-delta", majorVersion: 21 },
  assetIndex: {
    id: "17",
    sha1: "cccc000000000000000000000000000000000003",
    size: 450000,
    totalSize: 800000000,
    url: "https://piston-meta.mojang.com/v1/packages/cccc/17.json",
  },
  assets: "17",
  downloads: {
    client: { sha1: "dddd000000000000000000000000000000000004", size: 26836906, url: "https://piston-data.mojang.com/v1/objects/dddd/client.jar" },
  },
  logging: {
    client: {
      argument: "-Dlog4j.configurationFile=${path}",
      file: { id: "client-1.12.xml", sha1: "eeee000000000000000000000000000000000005", size: 888, url: "https://piston-data.mojang.com/v1/objects/eeee/client-1.12.xml" },
      type: "log4j2-xml",
    },
  },
  arguments: {
    game: [
      "--username",
      "${auth_player_name}",
      "--version",
      "${version_name}",
      "--gameDir",
      "${game_directory}",
      "--assetsDir",
      "${assets_root}",
      "--assetIndex",
      "${assets_index_name}",
      "--uuid",
      "${auth_uuid}",
      "--accessToken",
      "${auth_access_token}",
      "--clientId",
      "${clientid}",
      "--xuid",
      "${auth_xuid}",
      "--userType",
      "${user_type}",
      "--versionType",
      "${version_type}",
      { rules: [{ action: "allow", features: { is_demo_user: true } }], value: "--demo" },
      {
        rules: [{ action: "allow", features: { has_custom_resolution: true } }],
        value: ["--width", "${resolution_width}", "--height", "${resolution_height}"],
      },
      {
        rules: [{ action: "allow", features: { is_quick_play_singleplayer: true } }],
        value: ["--quickPlaySingleplayer", "${quickPlaySingleplayer}"],
      },
    ],
    jvm: [
      { rules: [{ action: "allow", os: { name: "osx" } }], value: ["-XstartOnFirstThread"] },
      {
        rules: [{ action: "allow", os: { name: "windows" } }],
        value: "-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump",
      },
      { rules: [{ action: "allow", os: { arch: "x86" } }], value: "-Xss1M" },
      "-Djava.library.path=${natives_directory}",
      "-Djna.tmpdir=${natives_directory}",
      "-Dminecraft.launcher.brand=${launcher_name}",
      "-Dminecraft.launcher.version=${launcher_version}",
      "-cp",
      "${classpath}",
    ],
  },
  libraries: [
    {
      name: "com.mojang:blocklist:1.0.10",
      downloads: {
        artifact: {
          path: "com/mojang/blocklist/1.0.10/blocklist-1.0.10.jar",
          sha1: "5c685c5ffa94c4cd39496c7184c1d122e515ecef",
          size: 964,
          url: "https://libraries.minecraft.net/com/mojang/blocklist/1.0.10/blocklist-1.0.10.jar",
        },
      },
    },
    {
      name: "org.ow2.asm:asm:9.3",
      downloads: {
        artifact: {
          path: "org/ow2/asm/asm/9.3/asm-9.3.jar",
          sha1: "8e6300ef51c1d801a7ed62d07cd221aca3a90640",
          size: 122004,
          url: "https://libraries.minecraft.net/org/ow2/asm/asm/9.3/asm-9.3.jar",
        },
      },
    },
    {
      name: "org.lwjgl:lwjgl:3.3.3",
      downloads: {
        artifact: {
          path: "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
          sha1: "29589b5f87ed335a6c7e7ee6a5775f81f97ecb84",
          size: 785029,
          url: "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
        },
      },
    },
    {
      name: "org.lwjgl:lwjgl:3.3.3:natives-windows",
      downloads: {
        artifact: {
          path: "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
          sha1: "a5ed18a2b82fc91b81f40d717cb1f64c9dcb0540",
          size: 165442,
          url: "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
        },
      },
      rules: [{ action: "allow", os: { name: "windows" } }],
    },
    {
      name: "org.lwjgl:lwjgl:3.3.3:natives-windows-arm64",
      downloads: {
        artifact: {
          path: "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows-arm64.jar",
          sha1: "e9aca8c5479b520a2a7f0d542a118140e812c5e8",
          size: 133378,
          url: "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows-arm64.jar",
        },
      },
      rules: [{ action: "allow", os: { name: "windows" } }],
    },
    {
      name: "org.lwjgl:lwjgl:3.3.3:natives-windows-x86",
      downloads: {
        artifact: {
          path: "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows-x86.jar",
          sha1: "9e670718e050aeaeea0c2d5b907cffb142f2e58f",
          size: 139653,
          url: "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows-x86.jar",
        },
      },
      rules: [{ action: "allow", os: { name: "windows" } }],
    },
    {
      name: "org.lwjgl:lwjgl:3.3.3:natives-macos-arm64",
      downloads: {
        artifact: {
          path: "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-macos-arm64.jar",
          sha1: "1111111111111111111111111111111111111111",
          size: 1,
          url: "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-macos-arm64.jar",
        },
      },
      rules: [{ action: "allow", os: { name: "osx" } }],
    },
  ],
};

export const v1122: VersionJson = {
  id: "1.12.2",
  type: "release",
  mainClass: "net.minecraft.client.main.Main",
  releaseTime: "2017-09-18T08:39:46+00:00",
  minecraftArguments:
    "--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userType ${user_type} --versionType ${version_type}",
  assetIndex: { id: "1.12", sha1: "ffff000000000000000000000000000000000006", size: 169014, url: "https://launchermeta.mojang.com/v1/packages/ffff/1.12.json" },
  downloads: { client: { sha1: "0f275bc1547d01fa5f56ba34bdc87d981ee12daf", size: 10180113, url: "https://launcher.mojang.com/v1/objects/0f27/client.jar" } },
  libraries: [
    {
      name: "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209",
      downloads: {
        artifact: {
          path: "org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar",
          sha1: "697517568c68e78ae0b4544145af031c81082dfe",
          size: 1047168,
          url: "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar",
        },
      },
      rules: [{ action: "allow" }, { action: "disallow", os: { name: "osx" } }],
    },
    {
      name: "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209",
      downloads: {
        classifiers: {
          "natives-linux": {
            path: "org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar",
            sha1: "931074f46c795d2f7b30ed6395df5715cfd7675b",
            size: 578680,
            url: "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar",
          },
          "natives-windows": {
            path: "org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar",
            sha1: "b84d5102b9dbfabfeb5e43c7e2828d98a7fc80e0",
            size: 613748,
            url: "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar",
          },
        },
      },
      extract: { exclude: ["META-INF/"] },
      natives: { linux: "natives-linux", osx: "natives-osx", windows: "natives-windows" },
    },
    {
      name: "tv.twitch:twitch-platform:6.5",
      downloads: {
        classifiers: {
          "natives-windows-64": {
            path: "tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-64.jar",
            sha1: "b5a5f3f3c3a3f3a3e3b3c3d3e3f3a3b3c3d3e3f3",
            size: 474225,
            url: "https://libraries.minecraft.net/tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-64.jar",
          },
        },
      },
      extract: { exclude: ["META-INF/"] },
      natives: { windows: "natives-windows-${arch}" },
    },
  ],
};

export const fabricProfile: VersionJson = {
  id: "fabric-loader-0.16.5-1.21.1",
  inheritsFrom: "1.21.1",
  releaseTime: "2024-09-15T00:00:00+0000",
  type: "release",
  mainClass: "net.fabricmc.loader.impl.launch.knot.KnotClient",
  arguments: { game: [], jvm: ["-DFabricMcEmu= net.minecraft.client.main.Main "] },
  libraries: [
    {
      name: "org.ow2.asm:asm:9.7.1",
      url: "https://maven.fabricmc.net/",
      sha1: "f0ed132a49244b042cd0e15702ab9f2ce3cc8436",
      size: 126093,
    },
    { name: "net.fabricmc:intermediary:1.21.1", url: "https://maven.fabricmc.net/" },
    { name: "net.fabricmc:fabric-loader:0.16.5", url: "https://maven.fabricmc.net/" },
  ],
};

export const assetIndex17: AssetIndex = {
  objects: {
    "icons/icon_16x16.png": { hash: "bdf48ef6b5d0d23bbb02e17d04865216179f510a", size: 3665 },
    "minecraft/sounds/ambient/cave/cave1.ogg": { hash: "4e9b4ab8e8e1e9a8d0e1c5f7c5e1b8d9a8f7e6d5", size: 13460 },
    "minecraft/sounds/dup.ogg": { hash: "bdf48ef6b5d0d23bbb02e17d04865216179f510a", size: 3665 },
  },
};

export const legacyIndex: AssetIndex = {
  virtual: true,
  objects: { "sounds/step/grass1.ogg": { hash: "227ab99bf7c6cf0b2002e0f7957d0ff7e5cb0c96", size: 6014 } },
};

export const javaIndex: JavaRuntimeIndex = {
  "windows-x64": {
    "java-runtime-delta": [
      {
        availability: { group: 1, progress: 100 },
        manifest: { sha1: "9999000000000000000000000000000000000009", size: 120000, url: "https://piston-meta.mojang.com/v1/packages/9999/manifest.json" },
        version: { name: "21.0.3", released: "2024-04-23T00:00:00+00:00" },
      },
    ],
  },
  linux: {},
};

export const javaManifest: JavaManifest = {
  files: {
    bin: { type: "directory" },
    "bin/javaw.exe": {
      type: "file",
      executable: true,
      downloads: { raw: { sha1: "1234567890123456789012345678901234567890", size: 20000, url: "https://piston-data.mojang.com/v1/objects/1234/javaw.exe" } },
    },
    "lib/modules": {
      type: "file",
      downloads: { raw: { sha1: "abcdefabcdefabcdefabcdefabcdefabcdefabcd", size: 40000000, url: "https://piston-data.mojang.com/v1/objects/abcd/modules" } },
    },
    "legal/LICENSE": { type: "link", target: "../LICENSE" },
  },
};
