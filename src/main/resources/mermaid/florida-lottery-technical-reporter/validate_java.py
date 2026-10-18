import re

with open("src/java/com/fllottery/util/DbObjectSearcher.java", "r") as f:
    text = f.read()

models = [
    "DbObject", "TableDocModel", "TableColumnDoc", "TableConstraintDoc",
    "TableIndexDoc", "TableTriggerDoc", "TableRelationship", "TableCatalogEntry",
    "RelationshipType", "ConfidenceLevel"
]

print("=== CHECKING MODELS ===")
for m in models:
    if f"class {m}" in text or f"enum {m}" in text:
        print(f"PASS: {m}")
    else:
        print(f"FAIL: {m}")

print("=== CHECKING BRACES ===")
open_b = text.count("{")
close_b = text.count("}")
print(f"Open braces: {open_b}, Close braces: {close_b}, Diff: {open_b - close_b}")

print("=== CHECKING ILLEGAL ESCAPES ===")
lines = text.splitlines()
errors = []

for idx, line in enumerate(lines, start=1):
    stripped = line
    if "//" in stripped:
        stripped = stripped[:stripped.index("//")]
    
    # Extract string literals
    in_str = False
    cur = []
    i = 0
    while i < len(stripped):
        ch = stripped[i]
        if not in_str:
            if ch == "\"":
                in_str = True
                cur = []
        else:
            if ch == "\\":
                if i + 1 < len(stripped):
                    next_ch = stripped[i+1]
                    if next_ch == "\\":
                        # Escaped backslash \ -> valid, advance past both
                        i += 2
                        continue
                    elif next_ch in ("n", "r", "t", "b", "f", "\"", "'"):
                        # Valid escape
                        i += 2
                        continue
                    elif next_ch == "u":
                        i += 6 # unicode escape
                        continue
                    else:
                        errors.append((idx, f"\\{next_ch}", line.strip()))
                        i += 2
                        continue
            elif ch == "\"":
                in_str = False
        i += 1

if errors:
    for idx, esc, l in errors:
        print(f"Line {idx}: illegal escape {esc} in {l}")
else:
    print("ALL STRINGS PRISTINE - ZERO ILLEGAL ESCAPES!")
