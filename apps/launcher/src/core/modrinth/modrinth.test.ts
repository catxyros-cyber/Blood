import { test } from "node:test";
import assert from "node:assert/strict";
import { ModrinthClient, buildFacets } from "./api";
import { compatibleVersions, planInstall } from "./resolve";
import { parseMrpackIndex } from "./mrpack";
import type { Project, Version } from "./types";
import { FakePlatform, json } from "../test/fakePlatform";

function version(over: Partial<Version> & { id: string; project_id: string }): Version {
  return {
    name: over.id,
    version_number: "1.0",
    version_type: "release",
    game_versions: ["1.21.1"],
    loaders: ["fabric"],
    featured: false,
    date_published: "2024-08-10T00:00:00Z",
    downloads: 0,
    files: [{ hashes: { sha1: `${over.id}-sha1`, sha512: "x" }, url: `https://cdn.modrinth.com/data/${over.id}.jar`, filename: `${over.id}.jar`, primary: true, size: 10 }],
    dependencies: [],
    ...over,
  };
}

function project(id: string, over: Partial<Project> = {}): Project {
  return {
    id,
    slug: id,
    title: id.toUpperCase(),
    description: "",
    body: "",
    categories: [],
    client_side: "required",
    server_side: "optional",
    project_type: "mod",
    downloads: 0,
    followers: 0,
    icon_url: null,
    gallery: [],
    game_versions: ["1.21.1"],
    loaders: ["fabric"],
    team: "t",
    published: "",
    updated: "",
    versions: [],
    ...over,
  };
}

test("facets: AND of ORs", () => {
  assert.deepEqual(
    buildFacets({ projectType: "mod", gameVersion: "1.21.1", loaders: ["quilt", "fabric"], categories: ["optimization"], clientOnly: true }),
    [["project_type:mod"], ["versions:1.21.1"], ["categories:quilt", "categories:fabric"], ["categories:optimization"], ["client_side:required", "client_side:optional"]],
  );
});

test("compatible versions prefer releases, then newest", () => {
  const vs = [
    version({ id: "beta-new", project_id: "p", version_type: "beta", date_published: "2024-09-01T00:00:00Z" }),
    version({ id: "rel-old", project_id: "p", date_published: "2024-07-01T00:00:00Z" }),
    version({ id: "rel-new", project_id: "p", date_published: "2024-08-01T00:00:00Z" }),
    version({ id: "forge", project_id: "p", loaders: ["forge"] }),
    version({ id: "old-mc", project_id: "p", game_versions: ["1.20.1"] }),
  ];
  assert.deepEqual(
    compatibleVersions(vs, { gameVersion: "1.21.1", loaders: ["fabric"] }).map((v) => v.id),
    ["rel-new", "rel-old", "beta-new"],
  );
});

test("install plan resolves required deps, reports optional, unresolved and conflicts", async () => {
  const p = new FakePlatform();
  const api = new ModrinthClient(p.request.bind(p), 0);
  const iris = version({
    id: "iris-1",
    project_id: "iris",
    dependencies: [
      { project_id: "sodium", version_id: null, file_name: null, dependency_type: "required" },
      { project_id: "modmenu", version_id: null, file_name: null, dependency_type: "optional" },
      { project_id: "optifine-ish", version_id: null, file_name: null, dependency_type: "incompatible" },
      { project_id: "nothing-for-121", version_id: null, file_name: null, dependency_type: "required" },
    ],
  });
  p.route("https://api.modrinth.com/v2/project/iris/version", json([iris]));
  p.route("https://api.modrinth.com/v2/project/sodium/version", json([version({ id: "sodium-1", project_id: "sodium" })]));
  p.route("https://api.modrinth.com/v2/project/nothing-for-121/version", json([]));
  for (const id of ["sodium", "modmenu", "optifine-ish", "nothing-for-121"]) {
    p.route(`https://api.modrinth.com/v2/project/${id}`, json(project(id)));
  }
  const plan = await planInstall(api, project("iris"), undefined, {
    target: { gameVersion: "1.21.1", loaders: ["fabric"] },
    installedProjectIds: new Set(["optifine-ish"]),
  });
  assert.deepEqual(plan.installs.map((i) => [i.projectId, i.reason]), [["iris", "requested"], ["sodium", "dependency"]]);
  assert.equal(plan.installs[1]!.requiredBy, "IRIS");
  assert.deepEqual(plan.optional.map((o) => o.projectId), ["modmenu"]);
  assert.deepEqual(plan.conflicts.map((c) => c.projectId), ["optifine-ish"]);
  assert.deepEqual(plan.unresolved.map((u) => u.projectId), ["nothing-for-121"]);
});

test("install plan skips deps already installed and errors when nothing is compatible", async () => {
  const p = new FakePlatform();
  const api = new ModrinthClient(p.request.bind(p), 0);
  p.route(
    "https://api.modrinth.com/v2/project/iris/version",
    json([version({ id: "iris-1", project_id: "iris", dependencies: [{ project_id: "sodium", version_id: null, file_name: null, dependency_type: "required" }] })]),
  );
  const plan = await planInstall(api, project("iris"), undefined, {
    target: { gameVersion: "1.21.1", loaders: ["fabric"] },
    installedProjectIds: new Set(["sodium"]),
  });
  assert.equal(plan.installs.length, 1);
  await assert.rejects(
    planInstall(api, project("iris"), undefined, { target: { gameVersion: "1.8.9", loaders: ["fabric"] }, installedProjectIds: new Set() }),
    (e: { code: string }) => e.code === "no_compatible_version",
  );
});

test("client retries after HTTP 429", async () => {
  const p = new FakePlatform();
  let calls = 0;
  p.route("https://api.modrinth.com/v2/project/sodium", () => {
    calls++;
    return calls === 1 ? json({}, 429, { "x-ratelimit-reset": "0", "x-ratelimit-remaining": "0" }) : json(project("sodium"));
  });
  const api = new ModrinthClient(p.request.bind(p), 0);
  const proj = await api.project("sodium");
  assert.equal(proj.id, "sodium");
  assert.equal(calls, 2);
});

test("mrpack: validates paths and hosts, skips server-only files, detects loader", () => {
  const index = {
    formatVersion: 1,
    game: "minecraft",
    versionId: "1.0.0",
    name: "MOTION Test Pack",
    dependencies: { minecraft: "1.21.1", "fabric-loader": "0.16.5" },
    files: [
      { path: "mods/sodium.jar", hashes: { sha1: "a", sha512: "b" }, env: { client: "required", server: "optional" }, downloads: ["https://cdn.modrinth.com/data/x/sodium.jar"], fileSize: 10 },
      { path: "mods/server-only.jar", hashes: { sha1: "c", sha512: "d" }, env: { client: "unsupported", server: "required" }, downloads: ["https://cdn.modrinth.com/s.jar"], fileSize: 1 },
    ],
  };
  const parsed = parseMrpackIndex(JSON.stringify(index), "C:\\inst", "\\");
  assert.equal(parsed.loader, "fabric");
  assert.equal(parsed.loaderVersion, "0.16.5");
  assert.equal(parsed.downloads.length, 1);
  assert.equal(parsed.downloads[0]!.path, "C:\\inst\\mods\\sodium.jar");
  assert.deepEqual(parsed.skipped, ["mods/server-only.jar"]);

  const evil = { ...index, files: [{ ...index.files[0]!, path: "../../Windows/evil.dll" }] };
  assert.throws(() => parseMrpackIndex(JSON.stringify(evil), "C:\\inst", "\\"), /unsafe file path/);
  const badHost = { ...index, files: [{ ...index.files[0]!, downloads: ["https://evil.example/sodium.jar"] }] };
  assert.throws(() => parseMrpackIndex(JSON.stringify(badHost), "C:\\inst", "\\"), /No allowed download source/);
});
