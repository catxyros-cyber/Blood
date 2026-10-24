/**
 * Minimal reader for Minecraft's NBT format (big-endian, Java Edition). Enough to read
 * servers.dat (uncompressed) and level.dat (gzip; decompress first with gunzip()).
 */

export type NbtValue = number | bigint | string | NbtValue[] | Int8Array | Int32Array | BigInt64Array | NbtCompound;
export interface NbtCompound {
  [key: string]: NbtValue;
}

const MAX_DEPTH = 64;

class Reader {
  private pos = 0;
  private readonly view: DataView;
  private readonly utf8 = new TextDecoder("utf-8");
  constructor(private readonly bytes: Uint8Array) {
    this.view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  }
  private need(n: number) {
    if (this.pos + n > this.bytes.length) throw new Error("NBT: unexpected end of data");
  }
  u8() {
    this.need(1);
    return this.view.getUint8(this.pos++);
  }
  i8() {
    this.need(1);
    return this.view.getInt8(this.pos++);
  }
  i16() {
    this.need(2);
    const v = this.view.getInt16(this.pos);
    this.pos += 2;
    return v;
  }
  u16() {
    this.need(2);
    const v = this.view.getUint16(this.pos);
    this.pos += 2;
    return v;
  }
  i32() {
    this.need(4);
    const v = this.view.getInt32(this.pos);
    this.pos += 4;
    return v;
  }
  i64() {
    this.need(8);
    const v = this.view.getBigInt64(this.pos);
    this.pos += 8;
    return v;
  }
  f32() {
    this.need(4);
    const v = this.view.getFloat32(this.pos);
    this.pos += 4;
    return v;
  }
  f64() {
    this.need(8);
    const v = this.view.getFloat64(this.pos);
    this.pos += 8;
    return v;
  }
  str() {
    const n = this.u16();
    this.need(n);
    // Java's "modified UTF-8" only differs for NUL and supplementary characters; plain UTF-8 is close enough for names.
    const s = this.utf8.decode(this.bytes.subarray(this.pos, this.pos + n));
    this.pos += n;
    return s;
  }
  len() {
    const n = this.i32();
    if (n < 0 || n > 16_777_216) throw new Error("NBT: bad length");
    return n;
  }

  payload(type: number, depth: number): NbtValue {
    if (depth > MAX_DEPTH) throw new Error("NBT: nested too deeply");
    switch (type) {
      case 1:
        return this.i8();
      case 2:
        return this.i16();
      case 3:
        return this.i32();
      case 4:
        return this.i64();
      case 5:
        return this.f32();
      case 6:
        return this.f64();
      case 7: {
        const n = this.len();
        this.need(n);
        const out = new Int8Array(this.bytes.buffer.slice(this.bytes.byteOffset + this.pos, this.bytes.byteOffset + this.pos + n));
        this.pos += n;
        return out;
      }
      case 8:
        return this.str();
      case 9: {
        const t = this.u8();
        const n = this.len();
        const out: NbtValue[] = [];
        for (let i = 0; i < n; i++) out.push(this.payload(t, depth + 1));
        return out;
      }
      case 10:
        return this.compound(depth + 1);
      case 11: {
        const n = this.len();
        const out = new Int32Array(n);
        for (let i = 0; i < n; i++) out[i] = this.i32();
        return out;
      }
      case 12: {
        const n = this.len();
        const out = new BigInt64Array(n);
        for (let i = 0; i < n; i++) out[i] = this.i64();
        return out;
      }
      default:
        throw new Error(`NBT: unknown tag type ${type}`);
    }
  }

  compound(depth: number): NbtCompound {
    const out: NbtCompound = {};
    for (;;) {
      const t = this.u8();
      if (t === 0) return out;
      const name = this.str();
      out[name] = this.payload(t, depth);
    }
  }

  /** The root: a named compound tag. */
  root(): NbtCompound {
    const t = this.u8();
    if (t !== 10) throw new Error("NBT: the root isn't a compound tag");
    this.str();
    return this.compound(0);
  }
}

export function readNbt(bytes: Uint8Array): NbtCompound {
  return new Reader(bytes).root();
}

/** True for gzip data (level.dat). servers.dat is stored uncompressed. */
export function isGzip(bytes: Uint8Array): boolean {
  return bytes.length > 2 && bytes[0] === 0x1f && bytes[1] === 0x8b;
}

export async function gunzip(bytes: Uint8Array): Promise<Uint8Array> {
  const stream = new Blob([bytes as BlobPart]).stream().pipeThrough(new DecompressionStream("gzip"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

/** Read an NBT file's bytes, decompressing gzip first when needed. */
export async function readNbtFile(bytes: Uint8Array): Promise<NbtCompound> {
  return readNbt(isGzip(bytes) ? await gunzip(bytes) : bytes);
}

// ---------------------------------------------------------------------------- typed helpers

export function nbtCompound(v: NbtValue | undefined): NbtCompound | undefined {
  return v && typeof v === "object" && !Array.isArray(v) && !ArrayBuffer.isView(v) ? (v as NbtCompound) : undefined;
}
export function nbtString(v: NbtValue | undefined): string | undefined {
  return typeof v === "string" ? v : undefined;
}
export function nbtNumber(v: NbtValue | undefined): number | undefined {
  return typeof v === "number" ? v : typeof v === "bigint" ? Number(v) : undefined;
}
export function nbtList(v: NbtValue | undefined): NbtValue[] {
  return Array.isArray(v) ? v : [];
}
