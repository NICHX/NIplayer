#!/usr/bin/env python3
"""Room 迁移链离线校验器。

作用：在不依赖 Android 运行时（Robolectric / 设备）的前提下，验证
`NiplayerDatabase` 中注册的迁移链能否把任意受支持的历史版本正确升到当前版本。

做法：
1. 从 `NiplayerDatabase.kt` 解析出每段 `Migration` 内 `db.execSQL(...)` 的 SQL
   （直接读源码，避免在脚本里复制一份 SQL 导致两边失同步）；
2. 用目标版本的 schema JSON 建出「升级前」的库（`schemas/<version>.json` 的 DDL 是
   Room 依据实体生成并导出的，等价于该版本全新安装的库）；
3. 在真实 SQLite 上按版本号顺序执行迁移；
4. 把迁移后的库结构与当前版本（最高版本 JSON）逐表、逐列、逐索引、逐外键比对。

比对规则对齐 Room 的 `TableInfo.equals`（已从 room-runtime 2.8.4 字节码确认）：
- 列按**名字**匹配，不比较物理顺序（Room 内部是 Map<String, Column>）；
- 比较 notNull、主键位置、类型亲和性（affinity）；
- **默认值仅在「期望列来自实体且期望默认值非 null」时比较** —— 因此实体未声明
  defaultValue 的列，迁移里多写一个 DEFAULT 不会导致校验失败（反之则会）。

用法：
    python3 verify_migrations.py            # 校验 6 -> 最新版本（目标版本由迁移链自动推导）
    python3 verify_migrations.py 10         # 校验 10 -> 最新版本
"""

from __future__ import annotations

import json
import re
import sqlite3
import sys
from pathlib import Path

MODULE_DIR = Path(__file__).resolve().parent.parent
KT_FILE = MODULE_DIR / "src/main/java/com/nichx/niplayer/database/NiplayerDatabase.kt"
SCHEMA_DIR = MODULE_DIR / "schemas/com.nichx.niplayer.database.NiplayerDatabase"

# Room 的 TableInfo 不管理该表，比对时忽略
IGNORED_TABLES = {"room_master_table", "android_metadata", "sqlite_sequence"}


# --------------------------------------------------------------------------- #
# 1. 从 Kotlin 源码解析迁移 SQL
# --------------------------------------------------------------------------- #

def _unescape_kotlin_string(raw: str, is_raw: bool) -> str:
    if is_raw:
        return raw
    out = []
    i = 0
    while i < len(raw):
        ch = raw[i]
        if ch == "\\" and i + 1 < len(raw):
            nxt = raw[i + 1]
            mapping = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "'": "'", "\\": "\\", "$": "$"}
            out.append(mapping.get(nxt, nxt))
            i += 2
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def _read_string_literal(src: str, pos: int) -> tuple[str, int]:
    """从 src[pos] 开始读一个 Kotlin 字符串字面量，返回 (值, 结束位置)。"""
    is_raw = src.startswith('"""', pos)
    if is_raw:
        end = src.index('"""', pos + 3)
        return _unescape_kotlin_string(src[pos + 3:end], True), end + 3
    assert src[pos] == '"', src[pos:pos + 20]
    i = pos + 1
    buf = []
    while i < len(src):
        if src[i] == "\\":
            buf.append(src[i:i + 2])
            i += 2
        elif src[i] == '"':
            break
        else:
            buf.append(src[i])
            i += 1
    return _unescape_kotlin_string("".join(buf), False), i + 1


def _read_exec_sql_arg(src: str, pos: int) -> tuple[str, int]:
    """读取 execSQL( ... ) 的实参，支持多段字符串用 + 拼接。"""
    depth = 1
    i = pos
    parts: list[str] = []
    while i < len(src):
        ch = src[i]
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return "".join(parts), i + 1
        elif ch == '"':
            value, i = _read_string_literal(src, i)
            parts.append(value)
            continue
        i += 1
    raise ValueError("execSQL 括号未闭合")


def parse_migrations(path: Path) -> dict[tuple[int, int], list[str]]:
    src = path.read_text(encoding="utf-8")
    migrations: dict[tuple[int, int], list[str]] = {}

    header = re.compile(r"val\s+MIGRATION_(\d+)_(\d+)\s*=\s*object\s*:\s*Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)")
    matches = list(header.finditer(src))
    if not matches:
        raise SystemExit(f"未在 {path} 中解析到任何 Migration")

    for idx, m in enumerate(matches):
        from_v, to_v = int(m.group(1)), int(m.group(2))
        assert (int(m.group(3)), int(m.group(4))) == (from_v, to_v), "MIGRATION_ 命名与 Migration(a, b) 不一致"
        block_end = matches[idx + 1].start() if idx + 1 < len(matches) else len(src)
        block = src[m.end():block_end]

        stmts: list[str] = []
        for call in re.finditer(r"execSQL\s*\(", block):
            arg, _ = _read_exec_sql_arg(block, call.end())
            stmts.append(arg.strip())
        migrations[(from_v, to_v)] = stmts

    return migrations


# --------------------------------------------------------------------------- #
# 2. 由 schema JSON 建库
# --------------------------------------------------------------------------- #

def load_schema(version: int) -> dict:
    return json.loads((SCHEMA_DIR / f"{version}.json").read_text(encoding="utf-8"))


def create_db_from_schema(conn: sqlite3.Connection, schema: dict) -> None:
    for entity in schema["database"]["entities"]:
        table = entity["tableName"]
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            conn.execute(index["createSql"].replace("${TABLE_NAME}", table))
    conn.commit()


def affinity(declared_type: str) -> str:
    """SQLite 类型亲和性规则（与 Room 的 TableInfo 计算方式一致）。"""
    t = (declared_type or "").upper()
    if "INT" in t:
        return "INTEGER"
    if "CHAR" in t or "CLOB" in t or "TEXT" in t:
        return "TEXT"
    if "BLOB" in t or t == "":
        return "BLOB"
    if "REAL" in t or "FLOA" in t or "DOUB" in t:
        return "REAL"
    return "NUMERIC"


# --------------------------------------------------------------------------- #
# 3. 读取实际库结构并比对
# --------------------------------------------------------------------------- #

def read_tables(conn: sqlite3.Connection) -> set[str]:
    rows = conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table'"
    ).fetchall()
    return {r[0] for r in rows if r[0] not in IGNORED_TABLES and not r[0].startswith("sqlite_")}


def read_columns(conn: sqlite3.Connection, table: str) -> dict[str, dict]:
    cols = {}
    for cid, name, ctype, notnull, dflt, pk in conn.execute(f"PRAGMA table_info(`{table}`)"):
        cols[name] = {
            "affinity": affinity(ctype),
            "notnull": bool(notnull),
            "pk": pk,
            "default": dflt,
        }
    return cols


def read_indices(conn: sqlite3.Connection, table: str) -> dict[str, tuple[bool, tuple[str, ...]]]:
    out = {}
    for row in conn.execute(f"PRAGMA index_list(`{table}`)"):
        # seq, name, unique, origin, partial
        name, unique = row[1], bool(row[2])
        if name.startswith("sqlite_autoindex"):
            continue
        cols = tuple(r[2] for r in conn.execute(f"PRAGMA index_info(`{name}`)"))
        out[name] = (unique, cols)
    return out


def read_foreign_keys(conn: sqlite3.Connection, table: str) -> set[tuple]:
    fks = set()
    for row in conn.execute(f"PRAGMA foreign_key_list(`{table}`)"):
        # id, seq, table, from, to, on_update, on_delete, match
        fks.add((row[2], row[3], row[4], row[5].upper(), row[6].upper()))
    return fks


def expected_columns(schema: dict, table: str) -> dict[str, dict]:
    """从 schema JSON 读出期望列。注意 Room 导出时：
    - `notNull` 仅在为 true 时写出；
    - `defaultValue` 仅在实体声明了 defaultValue 时写出；
    - 主键信息在 entity 层的 `primaryKey.columnNames`（字段上无 primaryKeyPosition）。
    """
    for entity in schema["database"]["entities"]:
        if entity["tableName"] != table:
            continue
        pk_columns = list(entity.get("primaryKey", {}).get("columnNames", []))
        cols = {}
        for field in entity["fields"]:
            name = field["columnName"]
            pk_pos = pk_columns.index(name) + 1 if name in pk_columns else 0
            cols[name] = {
                "affinity": affinity(field["affinity"]),
                "notnull": bool(field.get("notNull", False)),
                "pk": pk_pos,
                "default": field.get("defaultValue"),
            }
        return cols
    raise KeyError(table)


def expected_indices(schema: dict, table: str) -> dict[str, tuple[bool, tuple[str, ...]]]:
    for entity in schema["database"]["entities"]:
        if entity["tableName"] == table:
            return {
                i["name"]: (bool(i["unique"]), tuple(i["columnNames"]))
                for i in entity.get("indices", [])
            }
    raise KeyError(table)


# --------------------------------------------------------------------------- #
# 4. 主流程
# --------------------------------------------------------------------------- #

def resolve_start_schema(version: int) -> tuple[int, dict]:
    """取起始版本的 schema。

    `schemas/` 目录可能缺少某些版本的 JSON —— 例如 17.json 就不存在，因为 DB 版本从 16 直接
    跳到 18，KSP 从未为 v17 生成过 schema（这是该文件无法补出的原因，而非遗漏）。
    此时向下回退到最近一个可用的版本，并从那个版本开始跑迁移：由于 16→17 是空迁移，
    「从 v16 升级」与「从 v17 升级」在本迁移链上是同一件事。
    """
    v = version
    while v > 0:
        path = SCHEMA_DIR / f"{v}.json"
        if path.exists():
            if v != version:
                print(f"提示：{version}.json 不存在，改用 {v}.json 建库并从此版本开始迁移"
                      f"（{v} → {version} 之间为空迁移，两者等价）")
            return v, load_schema(v)
        v -= 1
    raise SystemExit(f"找不到任何 <= {version} 的 schema JSON")


def verify(start_version: int) -> int:
    migrations = parse_migrations(KT_FILE)
    target_version = max(v[1] for v in migrations)
    target_schema = load_schema(target_version)

    effective_start, start_schema = resolve_start_schema(start_version)

    chain = []
    v = effective_start
    while v < target_version:
        if (v, v + 1) not in migrations:
            print(f"✗ 迁移链断裂：缺少 {v} → {v + 1}")
            return 1
        chain.append((v, v + 1))
        v += 1

    print(f"迁移链：{effective_start} → {target_version}，共 {len(chain)} 段")
    for a, b in chain:
        print(f"  {a:>2} → {b:<2}  {len(migrations[(a, b)])} 条语句")

    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys = OFF")
    create_db_from_schema(conn, start_schema)

    # 埋入数据，验证迁移不丢数据（只写起始版本已存在的列；updated_at 由 v6→v7 补上）
    conn.execute(
        "INSERT INTO media_library (display_name, url, media_type, is_anonymous, port, "
        "smb_v2, smb_encryption, web_dav_strict, screencast_address) "
        "VALUES ('lib', 'smb://h/share', 'SMB', 0, 445, 1, 0, 0, '')"
    )
    conn.execute(
        "INSERT INTO play_history (video_name, url, media_type, video_position, video_duration, "
        "play_time, torrent_index, unique_key) "
        "VALUES ('m.mkv', 'smb://h/share/m.mkv', 'SMB', 1234, 5678, 0, 0, 'uk')"
    )
    conn.commit()

    for a, b in chain:
        for stmt in migrations[(a, b)]:
            try:
                conn.execute(stmt)
            except sqlite3.Error as exc:
                print(f"✗ 迁移 {a} → {b} 执行失败：{exc}\n  SQL: {stmt[:200]}")
                return 1
        conn.commit()

    problems: list[str] = []

    # 4.1 表集合
    actual_tables = read_tables(conn)
    expected_tables = {e["tableName"] for e in target_schema["database"]["entities"]}
    for missing in sorted(expected_tables - actual_tables):
        problems.append(f"缺少表 {missing}")
    for extra in sorted(actual_tables - expected_tables):
        problems.append(f"多出表 {extra}（目标版本已无此表）")

    # 4.2 逐表逐列
    for table in sorted(expected_tables & actual_tables):
        exp_cols = expected_columns(target_schema, table)
        act_cols = read_columns(conn, table)

        for missing in sorted(set(exp_cols) - set(act_cols)):
            problems.append(f"{table}: 缺少列 {missing}")
        for extra in sorted(set(act_cols) - set(exp_cols)):
            problems.append(f"{table}: 多出列 {extra}")

        for name in sorted(set(exp_cols) & set(act_cols)):
            e, a = exp_cols[name], act_cols[name]
            if e["notnull"] != a["notnull"]:
                problems.append(f"{table}.{name}: NOT NULL 期望 {e['notnull']} 实际 {a['notnull']}")
            if e["pk"] != a["pk"]:
                problems.append(f"{table}.{name}: 主键位置期望 {e['pk']} 实际 {a['pk']}")
            if e["affinity"] != a["affinity"]:
                problems.append(f"{table}.{name}: 亲和性期望 {e['affinity']} 实际 {a['affinity']}")
            # 对齐 Room：仅当期望默认值非 null 时才比较
            if e["default"] is not None and _norm_default(e["default"]) != _norm_default(a["default"]):
                problems.append(
                    f"{table}.{name}: 默认值期望 {e['default']!r} 实际 {a['default']!r}"
                )

        # 4.3 索引
        exp_idx = expected_indices(target_schema, table)
        act_idx = read_indices(conn, table)
        for missing in sorted(set(exp_idx) - set(act_idx)):
            problems.append(f"{table}: 缺少索引 {missing}")
        for extra in sorted(set(act_idx) - set(exp_idx)):
            problems.append(f"{table}: 多出索引 {extra}")
        for name in sorted(set(exp_idx) & set(act_idx)):
            if exp_idx[name] != act_idx[name]:
                problems.append(f"{table}: 索引 {name} 期望 {exp_idx[name]} 实际 {act_idx[name]}")

        # 4.4 外键
        exp_fk = {
            (fk["table"], fk["columnNames"][0], fk["referencedColumnNames"][0],
             (fk.get("onUpdate") or "NO ACTION").upper(), (fk.get("onDelete") or "NO ACTION").upper())
            for fk in next(e for e in target_schema["database"]["entities"]
                           if e["tableName"] == table).get("foreignKeys", [])
        }
        act_fk = read_foreign_keys(conn, table)
        if exp_fk != act_fk:
            problems.append(f"{table}: 外键 期望 {exp_fk or '{}'} 实际 {act_fk or '{}'}")

    # 4.5 数据保留
    row = conn.execute(
        "SELECT video_position, video_duration FROM play_history WHERE unique_key = 'uk'"
    ).fetchone()
    if row is None:
        problems.append("play_history 的既有数据在迁移后丢失")
    elif row != (1234, 5678):
        problems.append(f"play_history 数据被篡改：期望 (1234, 5678) 实际 {row}")

    if conn.execute("SELECT COUNT(*) FROM media_library").fetchone()[0] != 1:
        problems.append("media_library 的既有数据在迁移后丢失")

    conn.close()

    if problems:
        print(f"\n✗ 发现 {len(problems)} 处不一致：")
        for p in problems:
            print(f"  - {p}")
        return 1

    print(f"\n✓ {start_version} → {target_version} 迁移链校验通过")
    print(f"  表 {len(expected_tables)} 张、列/索引/外键/数据保留均与 {target_version}.json 一致")
    return 0


def _norm_default(value: str | None) -> str | None:
    """SQLite 会把默认值原样存为文本；统一去掉外层括号与引号差异。"""
    if value is None:
        return None
    v = value.strip()
    while len(v) >= 2 and v[0] == "(" and v[-1] == ")":
        v = v[1:-1].strip()
    if len(v) >= 2 and v[0] == "'" and v[-1] == "'":
        v = v[1:-1]
    return v


if __name__ == "__main__":
    start = int(sys.argv[1]) if len(sys.argv) > 1 else 6
    raise SystemExit(verify(start))
