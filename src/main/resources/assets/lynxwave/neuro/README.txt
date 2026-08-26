Bundled CatBoost files for Neuro rotation:
- delta_yaw_model.cbm
- delta_pitch_model.cbm
- model_meta.json

Runtime behavior:
- Primary source: assets (loaded directly from jar/resources).
- Fallback source: run/Lumora/files/neuro/*.cbm (only if assets are missing).

Neuro dataset samples now store:
- yaw/pitch deltas
- dtMs (timing per sample)
- horizontalSpeed
- onGround/sprinting
- bodyYawDelta/headYawDelta
