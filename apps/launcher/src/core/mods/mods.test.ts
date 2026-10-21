import { test } from "node:test";
import assert from "node:assert/strict";
import { combineMetadata, parseFabricModJson, parseModsToml, parseQuiltModJson, manifestVersion } from "./metadata";
import { analyzeMods, isFabricApiModule, type InstalledMod } from "./compat";
import { matchesMavenRange, matchesPredicate, compareVersions } from "../util/versions";
import { parseToml } from "../util/toml";

const sodiumJson = JSON.stringify({
  schemaVersion: 1,
  id: "sodium",
  version: "0.6.0",
  name: "Sodium",
  authors: ["JellySquid", { name: "IMS" }],
  icon: "sodium-icon.png",
  environment: "client",
  depends: { minecraft: ["~1.21.1", "1.21"], fabricloader: ">=0.16.0", "fabric-rendering-data-attachment-v1": "*" },
  breaks: { optifabric: "*" },
});

test("fabric.mod.json", () => {
  const m = parseFabricModJson(sodiumJson);
  assert.equal(m.id, "sodium");
  assert.deepEqual(m.authors, ["JellySquid", "IMS"]);
  assert.equal(m.environment, "client");
  assert.equal(m.depends.length, 3);
  assert.equal(m.icon, "sodium-icon.png");
  // raw control characters are tolerated
  assert.equal(parseFabricModJson('{"id":"x","description":"line\nbreak"}').id, "x");
});

test("quilt.mod.json", () => {
  const m = parseQuiltModJson(
    JSON.stringify({
      schema_version: 1,
      quilt_loader: {
        id: "qmod",
        version: "2.0",
        metadata: { name: "Q Mod", contributors: { Alice: "Owner" }, icon: { "32": "a.png", "128": "b.png" } },
        depends: ["quilt_loader", { id: "minecraft", versions: ">=1.20" }, { id: "qsl", optional: true }],
        provides: ["qmod_api"],
      },
      minecraft: { environment: "client" },
    }),
  );
  assert.equal(m.id, "qmod");
  assert.equal(m.icon, "b.png");
  assert.deepEqual(m.provides, ["qmod_api"]);
  assert.deepEqual(m.depends.map((d) => d.id), ["quilt_loader", "minecraft"]);
});

const neoToml = `
modLoader="javafml" #mandatory
loaderVersion="[4,)"
license="MIT"
[[mods]]
modId="examplemod"
version="\${file.jarVersion}"
displayName="Example Mod"
logoFile="logo.png"
description='''
A multi-line
description.
'''
[[dependencies.examplemod]]
    modId="neoforge"
    type="required"
    versionRange="[21.1,)"
    ordering="NONE"
    side="BOTH"
[[dependencies.examplemod]]
    modId="minecraft"
    type="required"
    versionRange="[1.21.1,1.22)"
    side="BOTH"
[[dependencies.examplemod]]
    modId="jei"
    type="optional"
`;

test("mods.toml / neoforge.mods.toml", () => {
  const [m] = parseModsToml(neoToml, "neoforge.mods.toml");
  assert.ok(m);
  assert.equal(m.id, "examplemod");
  assert.equal(m.name, "Example Mod");
  assert.deepEqual(m.loaders, ["neoforge"]);
  assert.equal(m.description, "A multi-line\ndescription.");
  assert.deepEqual(m.depends.map((d) => d.id), ["neoforge", "minecraft"]);
  // old Forge format uses `mandatory`
  const [f] = parseModsToml(`[[mods]]\nmodId="old"\n[[dependencies.old]]\nmodId="forge"\nmandatory=true\nversionRange="[47,)"\n`, "mods.toml");
  assert.deepEqual(f!.loaders, ["forge"]);
  assert.equal(f!.depends[0]!.id, "forge");
  assert.equal(manifestVersion("Manifest-Version: 1.0\nImplementation-Version: 3.2.1\n"), "3.2.1");
  assert.deepEqual(parseToml('a = [1, "two", true]\n[t]\nk = "v" # c').a, [1, "two", true]);
});

test("multi-loader jars report every loader", () => {
  const meta = combineMetadata({ "fabric.mod.json": sodiumJson, "META-INF/neoforge.mods.toml": neoToml });
  assert.deepEqual(meta?.loaders.sort(), ["fabric", "neoforge"]);
});

test("version predicates and ranges", () => {
  assert.equal(matchesPredicate("1.21.1", "~1.21.1"), true);
  assert.equal(matchesPredicate("1.21.2", "~1.21.1"), true);
  assert.equal(matchesPredicate("1.22", "~1.21.1"), false);
  assert.equal(matchesPredicate("1.21.1", ">=1.21 <1.22"), true);
  assert.equal(matchesPredicate("1.20.1", ">=1.21 <1.22"), false);
  assert.equal(matchesPredicate("1.21.1", "1.21.x"), true);
  assert.equal(matchesPredicate("1.20.4", ["1.20.1", "1.20.4"]), true);
  assert.equal(matchesPredicate("24w33a", ">=1.21"), null);
  assert.equal(matchesMavenRange("1.21.1", "[1.21.1,1.22)"), true);
  assert.equal(matchesMavenRange("1.22", "[1.21.1,1.22)"), false);
  assert.equal(matchesMavenRange("1.20.1", "[1.19,1.20),[1.20.1,)"), true);
  assert.equal(matchesMavenRange("1.20.1", "1.19"), true);
  assert.ok(compareVersions("1.10", "1.9") > 0);
});

function mod(fileName: string, metaJson?: string, enabled = true): InstalledMod {
  return { fileName, enabled, meta: metaJson ? parseFabricModJson(metaJson) : null };
}

test("compatibility analysis", () => {
  const fabricApi = mod("fabric-api.jar", JSON.stringify({ id: "fabric-api", version: "0.100", provides: ["fabric"] }));
  const iris = mod("iris.jar", JSON.stringify({ id: "iris", depends: { sodium: "*", "fabric-api": "*" } }));
  const sodium = mod("sodium.jar", sodiumJson);
  const issues = analyzeMods(
    [
      sodium,
      fabricApi,
      iris,
      mod("needs-cloth.jar", JSON.stringify({ id: "x", depends: { "cloth-config": "*" } })),
      mod("old.jar", JSON.stringify({ id: "old", depends: { minecraft: "1.20.1" } })),
      mod("sodium-copy.jar", sodiumJson),
      mod("OptiFine_1.21.1_HD_U_J1.jar"),
      mod("disabled-dupe.jar", sodiumJson, false),
      { fileName: "forge-mod.jar", enabled: true, meta: parseModsToml(neoToml, "neoforge.mods.toml")[0]! },
    ],
    { loader: "fabric", gameVersion: "1.21.1" },
  );
  const kinds = issues.map((i) => i.kind);
  assert.ok(kinds.includes("missing_dependency"));
  assert.ok(issues.some((i) => i.kind === "missing_dependency" && i.message.includes("cloth-config")));
  assert.ok(!issues.some((i) => i.kind === "missing_dependency" && i.message.includes("fabric-rendering")), "fabric api modules are satisfied");
  assert.ok(issues.some((i) => i.kind === "game_version" && i.files.includes("old.jar")));
  assert.ok(issues.some((i) => i.kind === "duplicate" && i.files.length === 2));
  assert.ok(issues.some((i) => i.kind === "known_conflict" && i.message.includes("OptiFine")));
  assert.ok(issues.some((i) => i.kind === "wrong_loader" && i.files.includes("forge-mod.jar")));
  assert.equal(issues[0]!.severity, "error");

  const quilt = analyzeMods([sodium, fabricApi], { loader: "quilt", gameVersion: "1.21.1" });
  assert.ok(!quilt.some((i) => i.kind === "wrong_loader"), "quilt accepts fabric mods");

  const vanilla = analyzeMods([sodium], { loader: "vanilla", gameVersion: "1.21.1" });
  assert.equal(vanilla[0]!.kind, "wrong_loader");
});

test("Fabric API modules vs other fabric- mods", () => {
  assert.ok(isFabricApiModule("fabric-resource-loader-v0"));
  assert.ok(isFabricApiModule("fabric-api-base"));
  assert.ok(!isFabricApiModule("fabric-language-kotlin"));
  const meta = (id: string, depends: Record<string, string>) => parseFabricModJson(JSON.stringify({ schemaVersion: 1, id, version: "1.0", depends }));
  const issues = analyzeMods(
    [
      { fileName: "fabric-api.jar", enabled: true, meta: meta("fabric-api", {}) },
      { fileName: "a.jar", enabled: true, meta: meta("a", { "fabric-resource-loader-v0": "*", "fabric-language-kotlin": ">=1.10" }) },
    ],
    { loader: "fabric", gameVersion: "1.21.1" },
  );
  const missing = issues.filter((i) => i.kind === "missing_dependency");
  assert.equal(missing.length, 1);
  assert.match(missing[0]!.message, /fabric-language-kotlin/);
});
