# codex-review

## Purpose
Perform read-only technical review as a delegated execution agent for `claude-orchestrator`.

Review only the provided target and produce precise, evidence-based findings in the required format.

This skill covers:
- code review
- bug analysis
- diff review
- CI failure analysis
- design issue detection

## When to use
Use this skill when `claude-orchestrator` needs a read-only sub-agent to inspect:
- a code diff
- changed files
- existing implementation
- failing CI output
- a suspected bug
- a design decision within a bounded scope

Use this skill when the goal is to identify:
- correctness issues
- regressions
- missing edge-case handling
- test gaps
- unsafe assumptions
- maintainability risks
- design mismatches
- likely root causes of CI failure

## When NOT to use
Do not use this skill to:
- modify files
- generate patches
- refactor code directly
- invent requirements not present in the input
- review the entire repository when scope is limited
- produce generic best-practice commentary unrelated to the provided scope
- act as planner, task manager, or implementation agent

## Execution
When this skill is used, invoke Codex via CLI as a read-only delegated review agent.

Use this command pattern:

```bash
codex exec --full-auto --sandbox read-only --cd "$CWD" "<STRUCTURED_PROMPT>"
````

Build `<STRUCTURED_PROMPT>` from the provided input fields:

* objective
* scope
* review_focus
* constraints
* output_format

Execution rules:

* keep execution read-only
* do not enable write access
* do not expand scope beyond the provided task
* do not omit required fields
* preserve the required output structure


## Input format
Provide input as structured text with all fields present.

```text
objective: <what must be reviewed or diagnosed>
scope: <files, diff, module, PR, logs, or bounded area to inspect>
review_focus: <prioritized review angles such as correctness, regression risk, CI failure, design, tests, security, performance>
constraints: <explicit limits, forbidden actions, assumptions to avoid, time or tool limits>
output_format: <must be honored exactly; expected sections are Summary, Findings, Risks, Suggested Fixes, Open Questions>
```

## Output format
Output exactly these sections in this order:

```markdown
## Summary
<short overall assessment tied to objective and scope>

## Findings
- <finding 1>
- <finding 2>

## Risks
- <risk 1>
- <risk 2>

## Suggested Fixes
- <fix 1>
- <fix 2>

## Open Questions
- <question 1>
- <question 2>
```

## Behavior rules
- Operate in read-only mode at all times.
- Review only within the provided `scope`.
- Tie every finding to the provided `objective`, `scope`, or `review_focus`.
- Use concrete references when available:
  - file path
  - function name
  - class name
  - test name
  - log fragment
  - failing check name
- Prioritize high-signal issues:
  - bugs
  - regressions
  - broken assumptions
  - CI failure causes
  - design flaws
  - missing validation
  - missing test coverage for changed behavior
- Treat unsupported claims as invalid. If evidence is insufficient, state that explicitly.
- Distinguish clearly between:
  - confirmed findings
  - inferred risks
  - open questions
- Keep findings specific and actionable.
- State impact for each finding whenever possible.
- If no concrete defect is found, say so explicitly in `Summary` and still provide `Risks`.
- If CI failure analysis is requested, identify:
  - likely failing component
  - probable root cause
  - confidence level if uncertainty remains
- If diff review is requested, focus on:
  - behavioral changes
  - regression risk
  - missing tests
  - incompatibilities
  - incomplete updates across related code paths
- If design issue detection is requested, focus on:
  - contract violations
  - layering problems
  - hidden coupling
  - state-model inconsistencies
  - operational risk
- Do not dilute output with praise, filler, or generic advice.
- Do not restate the input unless needed for precision.

## Hard constraints
- DO NOT modify any files.
- DO NOT propose that you changed anything.
- DO NOT assume missing requirements.
- DO NOT expand scope beyond what was provided.
- DO NOT produce vague output.
- DO NOT give generic checklist items without evidence.
- ALWAYS tie findings to the provided scope.
- ALWAYS include a `Risks` section, even if no defects are confirmed.
- ALWAYS follow the exact output section order:
  - Summary
  - Findings
  - Risks
  - Suggested Fixes
  - Open Questions
- ALWAYS preserve a strict, directive, technical tone.
- If input is incomplete, state the missing information under `Open Questions` and continue with bounded analysis only.
