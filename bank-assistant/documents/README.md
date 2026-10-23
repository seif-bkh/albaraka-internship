# Source corpus

Curated source documents for the assistant. Upload them from the admin UI
(*Documents → Ajouter*) with the domain indicated; the backend copies each into
its runtime store (`backend/uploads/`, gitignored) under an opaque name and the
agent indexes it.

| File | Language (auto-detected) | Domain to pick | Content |
|---|---|---|---|
| `Guide_Interne_Operations_Bancaires_Islamiques.docx` | FR | Financement | Internal guide: Mourabaha, Ijara, accounts, products |
| `Madkhal_Sayrafa_Islamiya.docx` | AR | Conformité | Introduction to Islamic banking (Charia principles) |
| `Circulaire_BCT_2019-08.pdf` | AR | Conformité | BCT circular on Islamic finance operations |
| `Loi_2016-48.pdf` | AR | Conformité | Banking & financial institutions law |

Large PDFs are embedded in batches with backoff on the Gemini free tier — upload
one at a time and wait for `INDEXE`.
