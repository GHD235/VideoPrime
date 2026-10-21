#!/usr/bin/env python3
"""VideoPrime: lee los resultados JUnit (XML) de los tests y falla solo si hay fallos que no
están en la lista de fallos conocidos (fallan igual en upstream o dependen de mocks viejos).

Uso: check_test_failures.py <carpeta de resultados> <lista de fallos conocidos>
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def main(results_dir, known_file):
    with open(known_file, encoding="utf-8") as f:
        known = {line.strip() for line in f if line.strip() and not line.startswith("#")}
    # "Clase#prefijo*" vale para todos los casos de un test parametrizado
    prefixes = [k[:-1] for k in known if k.endswith("*")]

    def is_known(name):
        return name in known or any(name.startswith(p) for p in prefixes)

    total = 0
    failed = set()
    for path in glob.glob(os.path.join(results_dir, "*.xml")):
        for case in ET.parse(path).getroot().iter("testcase"):
            total += 1
            if case.find("failure") is not None or case.find("error") is not None:
                failed.add(f'{case.get("classname")}#{case.get("name")}')

    if total == 0:
        print(f"::error::No hay resultados de tests en {results_dir}")
        return 1

    new = sorted(name for name in failed if not is_known(name))
    fixed = sorted(k for k in known
                   if not any(name == k or (k.endswith("*") and name.startswith(k[:-1]))
                              for name in failed))
    print(f"{total} tests, {len(failed)} fallos ({len(failed) - len(new)} conocidos, {len(new)} nuevos)")
    for name in fixed:
        print(f"::notice::Ya no falla (se puede quitar de la lista): {name}")
    for name in new:
        print(f"::error::Fallo nuevo: {name}")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write(f"**Extractor (MOCK):** {total} tests, {len(failed)} fallos, "
                      f"{len(new)} nuevos\n\n")
            out.writelines(f"- Nuevo: `{name}`\n" for name in new)
    return 1 if new else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
