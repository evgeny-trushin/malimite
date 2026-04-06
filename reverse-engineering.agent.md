nalyze this codebase deeply and produce a documentation set that captures the technical architecture, dependencies, runtime flow, data flow, and important logic.

Goals:
1. Inspect the codebase and identify:
   - first-party code areas
   - major architecture layers
   - core logic and runtime behavior
   - directly used libraries and bundled/transitive libraries
   - important schemas, data contracts, serialization, transport, or integration logic
   - persistence, state flow, background processes, and communication boundaries if present
2. Extract and store as much useful technical information as possible in a docs folder.
3. Keep the documentation neutral and product-facing:
   - do not describe the work using provenance language
   - do not mention how the source snapshot was obtained
   - remove references to commercial, billing, payment, subscription, entitlement, or monetization systems
   - remove references to vendor-specific commercial platforms
4. If the docs contain an old product name, rename it to the new product name in the documentation only.
   - do not rewrite actual source code, real identifiers, or real file paths unless explicitly asked
   - if old names appear only inside source paths or identifiers, describe them neutrally instead of exposing them unnecessarily
5. Preserve the technical value:
   - architecture
   - core logic
   - dependency inventory
   - schema or contract definitions
   - state-machine or workflow behavior
   - notable risks, inconsistencies, or incomplete areas
6. Verify the result:
   - scan the docs for banned terms
   - validate machine-readable files
   - report what was changed

Create a documentation set with:
- an index/overview document
- an architecture deep dive
- a library/dependency inventory
- a protocol/schema/data-contract note
- a reconstructed reference schema or contract file if relevant
- a machine-readable summary file

Content requirements:
- Overview doc: concise index, key findings, document map, important areas, counts where useful
- Architecture doc: system shape, layers, runtime flow, state, persistence, UI or interface structure if present, integration points, risks
- Library doc: direct dependencies, bundled/transitive libraries, exposed versions, what materially affects product behavior
- Protocol/schema doc: schema notes, envelope/message structure, flow behavior, validation rules, security or trust observations if relevant
- Reference contract file: clean reconstruction of the key schema/contract when useful
- Machine-readable summary: major facts captured in a structured format

Important constraints:
- Be precise and concrete.
- Prefer source-backed statements over guesses.
- If something is inferred, label it as an inference.
- Keep the docs clean, neutral, and reusable.
- Do not include provenance language, monetization language, or vendor-specific commercial references in the final docs.

At the end:
- summarize what you created
- list the output files
- mention verification results