# Third-Party Licenses

Keyword Extractor uses third-party open-source libraries, bundles a few
third-party resources, and relies on external services at runtime.

The license of this project does not replace or modify the licenses of
third-party components. Each component remains subject to its own license
terms.

## Direct dependencies

| Component | Maven coordinates | Version | License (SPDX) | Notes |
|---|---|---|---|---|
| Spring Boot | `org.springframework.boot:spring-boot-starter`, `spring-boot-starter-web` | 3.5.16 | Apache-2.0 | |
| Project Lombok | `org.projectlombok:lombok` | 1.18.46 (managed by Spring Boot) | MIT | Compile-time only; excluded from the executable jar |
| iText Core | `com.itextpdf:itext-core` | 9.3.0 | AGPL-3.0 (or commercial license) | See the iText notice below |
| Jackson Databind | `tools.jackson.core:jackson-databind` | 3.2.3 | Apache-2.0 | |
| Apache Tika Core | `org.apache.tika:tika-core` | 3.3.1 | Apache-2.0 | |
| Apache Tika language detection (Optimaize) | `org.apache.tika:tika-langdetect-optimaize` | 3.3.1 | Apache-2.0 | Brings `com.optimaize.languagedetector:language-detector` 0.6 (Apache-2.0) |
| Apache OpenNLP Tools | `org.apache.opennlp:opennlp-tools` | 2.5.11 | Apache-2.0 | |

## Transitive dependencies

Runtime dependencies resolved from `pom.xml` (`mvn dependency:list -DincludeScope=runtime`), grouped by license.
Regenerate this list whenever dependencies change.

| License (SPDX) | Components |
|---|---|
| Apache-2.0 | Spring Framework 6.2.19, Apache Tomcat Embed 10.1.55, Jackson 2.21.x (`com.fasterxml.jackson`, pulled by Spring Boot), Jackson 3.2.3 core, Micrometer 1.15.12, Guava 18.0, Apache Log4j API and Log4j-to-SLF4J 2.24.3, Apache Santuario xmlsec 3.0.6, Apache Commons Codec 1.18.0, Apache Commons IO 2.22.0, SnakeYAML 2.4, Woodstox 6.5.1, JSONIC 1.2.11, JetBrains Annotations 26.1.0 |
| MIT | SLF4J 2.0.18 (`slf4j-api`, `jul-to-slf4j`) |
| BSD | Stax2 API 4.2.1 |
| BSD-3-Clause (Eclipse Distribution License 1.0) | Jakarta Activation API 2.1.4, Jakarta XML Binding API 4.0.5 |
| EPL-2.0 OR LGPL-2.1-only | Logback 1.5.34 (`logback-classic`, `logback-core`) |
| EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 | Jakarta Annotations API 2.1.1 |
| AGPL-3.0 | iText 9.3.0 modules (`kernel`, `io`, `layout`, `forms`, `sign`, `svg`, `pdfa`, `pdfua`, `barcodes`, `commons`, `styled-xml-parser`, `bouncy-castle-connector`) |
| Various (see individual files) | iText 9.3.0 `hyph` (hyphenation patterns from several sources) and `font-asian` (CJK CMaps) |

## Embedded resources

| Path | Origin | License | Notes |
|---|---|---|---|
| `src/main/resources/en/pos-model.bin` | Apache OpenNLP pre-trained model `opennlp-en-ud-ewt-pos-1.3-2.5.4.bin` (https://opennlp.apache.org/models.html) | Apache-2.0 | Unmodified (renamed only). Trained by the Apache OpenNLP project on the Universal Dependencies English EWT treebank. NOTICE reproduced in `NOTICE.md`; license text in `LICENSES/Apache-2.0.txt` |
| `src/main/resources/fr/pos-model.bin` | Apache OpenNLP pre-trained model `opennlp-fr-ud-gsd-pos-1.3-2.5.4.bin` (https://opennlp.apache.org/models.html) | Apache-2.0 | Unmodified (renamed only). Trained by the Apache OpenNLP project on the Universal Dependencies French GSD treebank. NOTICE reproduced in `NOTICE.md`; license text in `LICENSES/Apache-2.0.txt` |
| `src/main/resources/{en,fr}/stopwords.txt` | Compiled for this project: initially drafted with AI assistance, then reviewed and adjusted manually | Distributed with this project (AGPL-3.0-or-later) | Lists of common function words; may overlap with widely used public stopword lists |
| `src/main/resources/{en,fr}/generic-terms.txt` | Same as above | Same as above | Lists of common generic terms used to filter keyword candidates |

## Runtime services and AI models

These components are not distributed with this project. They must be installed
separately by the user and remain subject to their own license terms.

| Component | Role | Provider | License |
|---|---|---|---|
| Ollama | Local inference server (`http://localhost:11434`) | Ollama | MIT |
| `bge-m3` | Semantic embeddings | BAAI | MIT |
| `qwen2.5:7b` | LLM refinement (`quality` mode) | Alibaba Cloud (Qwen) | Apache-2.0 |

Check each model card before use: model licenses may differ between sizes and
versions of the same model family.

## Important notice regarding iText

iText is distributed under the GNU Affero General Public License or under
a commercial license.

This project uses the AGPL-licensed version of iText.

Users who redistribute or modify this project must comply with the applicable
iText license terms in addition to the license terms applicable to this
project.

For commercial use cases where the AGPL obligations are not acceptable,
users should review iText's commercial licensing options.

## License texts

- This project: `LICENSE` (AGPL-3.0-or-later).
- Apache-2.0, for the bundled Apache OpenNLP models: `LICENSES/Apache-2.0.txt`.
- Maven dependencies are not bundled in this repository; their license texts
  are distributed with each artifact (usually `META-INF/LICENSE*` and
  `META-INF/NOTICE*`) and published by the corresponding projects.
