"""Check graph links, edge agreement, execution plan and the independent noise fixture."""
import json
import re

from fixture import check_fixture
from journal import event
from state import load_plan

BUILD_KEYS = (
    "build_status", "build_agent", "build_pid", "build_pid_note", "build_attempt",
    "build_started_at", "build_ended_at", "build_verified_at", "build_requested_action",
    "build_request_id", "build_acknowledged_request_id", "build_heartbeat_at",
    "build_files", "build_last_error", "build_evidence",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_graph(root):
    folder = root / "docs/logic-contracts"
    notes = {p.stem: p.read_text() for p in folder.glob("*.md")}
    require("00-logic-map" in notes, "Missing graph map")
    heads = {name: set(re.findall(r"^#{1,6} (.+)$", text, re.M)) for name, text in notes.items()}
    ids, links = {}, 0
    for name, text in notes.items():
        parts = text.split("---", 2)
        require(len(parts) == 3 and not parts[0].strip(), f"Missing frontmatter: {name}")
        fields = dict(re.findall(r"^(\w+):[ \t]*([^\n]*)$", parts[1], re.M))
        require(all(key in fields for key in BUILD_KEYS), f"Missing build properties: {name}")
        require(re.fullmatch(r"N\d+", fields.get("id", "")), f"Missing node ID: {name}")
        require(fields['id'] not in ids.values(), f"Duplicate node ID: {name}")
        require(fields['build_status'] in ("not-built", "building", "paused", "blocked", "failed", "cancelled", "built"),
                f"Invalid build status: {name}")
        ids[name] = fields['id']
        for target in re.findall(r"\[\[([^\]]+)\]\]", text):
            node, *heading = target.split('|')[0].split('#', 1)
            require(node in notes, f"Broken node link: {name} -> {target}")
            require(not heading or heading[0] in heads[node], f"Broken heading link: {name} -> {target}")
            links += 1
        for href in re.findall(r"(?<!!)\[[^\]]+\]\(([^)]+)\)", text):
            if '://' not in href:
                require((folder / href.split('#')[0]).exists(), f"Missing linked file: {name} -> {href}")
    contracts = {}
    for name, text in notes.items():
        for match in re.finditer(r"^### (C\d+) ([^\n]+)\n(.*?)(?=^### |^## |\Z)", text, re.M | re.S):
            edge, title, body = match.groups()
            consumer = re.search(r"\*\*Consumer:\*\* \[\[([^\]]+)\]\]", body)
            require(edge not in contracts and consumer is not None, f"Duplicate or incomplete contract: {edge}")
            target = consumer[1]
            require(target in notes, f"Missing consumer: {edge}")
            require('**Edge assertion:**' in body, f"Missing edge assertion: {edge}")
            require(f'[[{name}#{edge} {title}]]' in notes[target], f"Missing consumer backlink: {edge}")
            contracts[edge] = (ids[name], ids[target])
    index = {edge: ('N' + a, 'N' + b) for edge, a, b in
             re.findall(r'^\| (C\d+) \| (\d+) \| (\d+) \|', notes['00-logic-map'], re.M)}
    diagram = {edge: (a, b) for a, edge, b in
               re.findall(r'(N\d+)(?:\[[^\n]*?\])? -->\|"(C\d+) [^"]+"\| (N\d+)', notes['00-logic-map'])}
    require(contracts == index == diagram, "Contract definitions, index and diagram disagree")
    require(set(contracts) == {f'C{i:02d}' for i in range(1, 16)}, "Expected contracts C01-C15")
    return dict(notes=len(notes), contracts=len(contracts), wikilinks=links)


def validate(root, plan_path):
    graph = check_graph(root)
    plan, _, digest = load_plan(root, plan_path)
    result = dict(graph=graph, tasks=len(plan['tasks']), plan_hash=digest, noise_fixture=check_fixture())
    directory = root / '.supervisor'
    directory.mkdir(exist_ok=True)
    (directory / 'validation.json').write_text(json.dumps(result, indent=2) + '\n')
    event(directory, 'graph-validation', **result)
    print(json.dumps(result, indent=2))
    return 0
