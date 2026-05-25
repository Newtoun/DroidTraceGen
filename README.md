# DroidTraceGen

## English

**DroidTraceGen** is a tool that records UI actions on Android devices via the Accessibility API and automatically generates TDD-structured test cases using a large language model (LLaMA 3.1 70B).

### Repository Structure

| Name | Description |
|------|-------------|
| `DroidTraceGen/` | Android app source code — records UI actions and sends logs to the backend server |
| `BackendSocket/` | Python backend server — receives logs from the app, stores them, and generates TDD test cases via LLM |
| `DroidTraceGen.apk` | Ready-to-install Android APK — install directly on any Android device to use the tool without building from source |

### Quick Start

1. Install `DroidTraceGen.apk` on your Android device
2. Start the backend server (see `BackendSocket/README.md`)
3. Open the app, grant the required permissions, and start recording
4. Stop the recording — the log is sent automatically to the server
5. Run `prompt.py` on the server to generate the TDD test case

---

## Português

**DroidTraceGen** é uma ferramenta que grava ações de interface em dispositivos Android via Accessibility API e gera automaticamente casos de teste no formato TDD usando um modelo de linguagem (LLaMA 3.1 70B).

### Estrutura do Repositório

| Nome | Descrição |
|------|-----------|
| `DroidTraceGen/` | Código-fonte do app Android — grava ações de UI e envia os logs para o servidor backend |
| `BackendSocket/` | Servidor backend em Python — recebe os logs do app, os armazena e gera casos de teste TDD via LLM |
| `DroidTraceGen.apk` | APK Android pronto para instalar — instale diretamente em qualquer dispositivo Android para usar a ferramenta sem precisar compilar o código |

### Início Rápido

1. Instale o `DroidTraceGen.apk` no seu dispositivo Android
2. Inicie o servidor backend (veja `BackendSocket/README.md`)
3. Abra o app, conceda as permissões necessárias e inicie a gravação
4. Pare a gravação — o log é enviado automaticamente ao servidor
5. Execute `prompt.py` no servidor para gerar o caso de teste TDD
