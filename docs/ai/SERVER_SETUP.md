# Preparar el servidor de casa para que Claude trabaje en autónomo

Checklist para Yago. Se hace una vez. Tiempo estimado: 30-45 min.

## 0. En el portátil (antes de nada)
La rama `chore/ai-workflow-setup` solo existe en el portátil: hay que subirla.
1. Instala y autentica `gh` (sección 2).
2. `cd ~/Yago/personal/asaltamontes && git push -u origin chore/ai-workflow-setup`
3. Crea la PR: `gh pr create --base main --title "docs: AI-assisted workflow, roadmap, task specs and bug registry" --body-file .ai/pr-bodies/chore-ai-workflow-setup.md`
4. Copia el estado local al servidor más adelante (paso 5): la carpeta `.ai/` del portátil.

## 1. Herramientas en el servidor (Raspberry Pi 5, ARM64)
Antes de instalar:
- `free -h` y `df -h`: anota la RAM y el disco en STATUS. Con 4 GB, Claude trabajará en secuencial; con 8 GB o más, hasta 2 agentes.
- Muy recomendable: el sistema y Docker en **SSD/NVMe**, no en la microSD (los builds y Postgres sufren mucho en SD).
- Swap de al menos 2-4 GB (`sudo dphys-swapfile` en Raspberry Pi OS, o zram).

```bash
sudo apt update && sudo apt install -y git tmux curl jq
# Java 21: prueba `sudo apt install -y openjdk-21-jdk`; si tu versión de Raspberry Pi OS no lo tiene:
#   curl -s "https://get.sdkman.io" | bash && source ~/.sdkman/bin/sdkman-init.sh && sdk install java 21-tem
java -version   # debe ser 21
# Node 24 (si no está): https://github.com/nodesource/distributions o nvm
node -v   # debe ser v24.x
# Docker: ya está (corre la app). Tu usuario debe estar en el grupo docker:
groups | grep -q docker || { sudo usermod -aG docker $USER; echo "cierra sesión y vuelve a entrar"; }
# Claude Code
curl -fsSL https://claude.ai/install.sh | bash
```

## 2. GitHub CLI (`gh`): igual en el portátil y en el servidor
```bash
(type -p wget >/dev/null || sudo apt install wget -y) \
 && sudo mkdir -p -m 755 /etc/apt/keyrings \
 && out=$(mktemp) && wget -nv -O"$out" https://cli.github.com/packages/githubcli-archive-keyring.gpg \
 && cat "$out" | sudo tee /etc/apt/keyrings/githubcli-archive-keyring.gpg > /dev/null \
 && sudo chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg \
 && sudo mkdir -p -m 755 /etc/apt/sources.list.d \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
    | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
 && sudo apt update && sudo apt install gh -y

gh auth login
#  → GitHub.com → HTTPS → "Authenticate Git with your GitHub credentials?" Yes
#  → "Login with a web browser": copia el código de 8 caracteres. En un servidor sin
#    navegador, abre https://github.com/login/device en el móvil o el portátil y pégalo.
gh auth status        # comprobar
gh auth setup-git     # que git push use gh como credencial
```

**Cuenta:** Claude usa la cuenta de Yago (tiene configurado poder aprobar sus propias PRs).

## 3. Proteger `main` en GitHub
✅ **Ya configurado por Yago** (protección de `main`, checks obligatorios y auto-aprobación permitida). Referencia de lo esperado:
- Target: `main`. Enforcement: Active.
- ✅ Restrict deletions · ✅ Block force pushes · ✅ Require a pull request before merging (0 approvals si usas tu cuenta; 1 si usas bot).
- Required status checks: los dos jobs de `ci.yml` (`validate-backend`, `validate-frontend`).
- Allowed merge methods: **Squash** únicamente.

Así, aunque algo fallara, nadie puede hacer push directo a `main`: solo entra lo que tú mergeas.

## 4. Clon de trabajo de Claude (separado del runner)
```bash
mkdir -p ~/work && cd ~/work
git clone https://github.com/YagoGefaell/asaltamontes.git
cd asaltamontes && git switch chore/ai-workflow-setup   # hasta que la mergees
```
**No uses** la carpeta `_work/` del runner self-hosted: es la del despliegue.

## 5. Estado y contexto
- Copia `.ai/` del portátil al servidor: `scp -r ~/Yago/personal/asaltamontes/.ai <usuario>@<ip-servidor>:~/work/asaltamontes/`
- La memoria de Claude va por ruta de proyecto. Opcional: tras abrir `claude` una vez en `~/work/asaltamontes`, copia los archivos de `~/.claude/projects/-home-yagogefaell-Yago-personal-asaltamontes/memory/` del portátil a la carpeta equivalente del servidor (`~/.claude/projects/<ruta-del-servidor>/memory/`). Lo importante ya está en el repo y en `.ai/`.
- Anota en `.ai/STATUS.md` → "Notas de entorno":
  - IP del servidor en la LAN.
  - Ruta del checkout del runner y nombre del proyecto de compose de producción (`docker compose ls`).
  - Ruta del archivo de datos de ejemplo de la BD (para T02).
  - RAM y tipo de disco de la Pi (`free -h`, SD o SSD).

## 6. Arrancar la sesión autónoma
```bash
tmux new -s claude          # sobrevive a que cierres el SSH; volver: tmux attach -t claude
cd ~/work/asaltamontes
claude
```
Dentro de Claude Code:
0. Tailscale (para T07, cuando quieras): `curl -fsSL https://tailscale.com/install.sh | sh && sudo tailscale up`. No requiere abrir puertos en el router.
1. Modo de permisos: **auto** (Shift+Tab hasta "auto mode"). Las reglas de `.claude/settings.json` bloquean igualmente lo peligroso: push a main, merge, `docker compose down`, borrar volúmenes, `rm -rf`.
2. Opcional: `/remote-control` para seguirlo y contestarle desde el móvil.
3. Escribe **`/autopilot`**. Trabajará hasta quedarse sin trabajo seguro, bloqueado o en el límite de PRs, y dejará todo registrado.

## 7. Tu rutina de revisión
- Revisa las PRs en GitHub (comentarios en línea o generales) y mergea con **Squash**.
- En la siguiente sesión (o en la misma, si sigue corriendo), Claude lee tus comentarios y los aplica (`/address-review`).
- Las preguntas que te haga quedan en `.ai/STATUS.md` → "Bloqueos y preguntas abiertas".
