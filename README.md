# Amamenta Bebê — Android 1.1

Continuação do aplicativo original. `applicationId` permanece `br.com.amamentabebe`; `versionCode` passa de 1 para 2. Não há migração destrutiva.

## Recursos locais

- Alimentação rápida protegida contra toque repetido; peito esquerdo/direito/ambos, mamadeira, ml, observações, data, horário e duração manual.
- Cronômetro com início, pausa, continuação, troca de lado e finalização. Timestamps persistidos mantêm durações por lado e total com tela bloqueada. Aviso aos 30 minutos sem finalizar automaticamente.
- Fraldas xixi, cocô ou ambos (uma única troca); resumo e histórico com filtros, edição e exclusão. Fraldas não alteram lembretes.
- Modo Madrugada automático entre 22h e 6h, sempre ligado ou desligado.
- AlarmManager, notificações, adiamentos 10/20 min e recuperação após reinicialização. Permissões negadas e alarme aproximado são informados; Android/fabricantes podem restringir entrega.
- Widget com última/próxima alimentação, horário programado e registro rápido. Atualização por eventos sem polling contínuo.
- Backup JSON pelo seletor Android; validação antes de gravar, mesclagem e deduplicação. Preserva alarmes/cronômetro atuais; restaura preferências após confirmação.
- Sobre → Apoie o desenvolvedor: seção preparada, Pix desativado até receber código real e beneficiário. Com propriedades reais `PIX_COPY_PASTE`/`PIX_BENEFICIARY`, mostra QR gerado do código e botão para copiar. Contribuição opcional sem bloquear recursos.

## Preservação e assinatura

Room mantém `amamenta-bebe.db`, tabela `feedings` e campos originais; DataStore mantém `settings`. Migração 1→2 adiciona durações com zero padrão e novas tabelas. Testes atualizam banco real no esquema v1 e verificam conteúdo.

O workflow antigo gerava chave debug temporária. O certificado do último artifact v1 está em [docs/signing-baseline.json](docs/signing-baseline.json). Isso **não comprova** o certificado instalado no celular nem recupera a chave privada.

**Não instale o APK de validação sobre o app com dados reais sem assinatura verificada. Não desinstale para contornar incompatibilidade.** A publicação do artifact `Amamenta-Bebe-APK` exige:

- Secrets Actions: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` com a chave original.
- Variable Actions: `INSTALLED_CERT_SHA256`, confirmado no APK instalado.
- O workflow compara o certificado com `apksigner` e publica somente se coincidir. Sem chave, publica relatórios `Amamenta-Bebe-Validation`, sem APK distribuído.

Se a chave privada do runner antigo não foi preservada, recuperar seu APK recupera apenas o certificado público. A atualização compatível fica bloqueada; preserve aplicativo e dados.

## Família opcional

Código preparado para acesso pessoal Firebase Auth e-mail/senha com e-mail verificado; administrador/cuidador; família/bebê; convites vinculados ao e-mail, token aleatório de 256 bits com hash SHA-256, validade inferior a 24h, aceite transacional de uso único, revogação e remoção de participantes.

Room continua local. Fila persistida mantém UUID, autoria, revisão, pendência e tombstones. WorkManager tenta sincronizar com conexão, a cada 15 minutos ou após mudança; botão manual disponível. Conflitos preservam edição local até escolha explícita. Registros enviados permanecem no Room. **Histórico anterior só entra na fila após confirmação na interface.**

Família fica **desativada sem configuração real**. Nenhum projeto/serviço foi criado ou implantado; não houve validação em dois celulares. Emulador verifica regras, não substitui testes Android completos da sincronização. Não declarar compartilhamento concluído antes da validação integrada.

Configure Firebase Auth e Firestore Standard conforme [backend/README.md](backend/README.md), publique regras revisadas e forneça propriedades Gradle/variáveis reais `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID` (também Variables do Actions). Não há credenciais fictícias. Não habilite cobrança sem autorização.

Regras são protótipo testado para isolamento entre famílias, autoria imutável, papéis e convites temporários. Revise antes de distribuição ampla. Remover um participante revoga acesso remoto; dados já baixados não podem ser apagados remotamente com garantia.

## Compilar e testar

JDK 17, Android SDK 35; Kotlin/Compose compiler 2.3.20 e KSP 2.3.4.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
npm ci --prefix backend --ignore-scripts
npm run test:emulator --prefix backend
```

Windows: `gradlew.bat`. APK **local de validação** em `app/build/outputs/apk/debug/app-debug.apk`; sem chave original usa debug local, sem garantia de atualização. Emulador usa apenas `demo-amamenta-bebe`, sem implantação remota. CI executa regras, JVM/Robolectric, lint e build. [Auditoria](docs/V1.1-AUDIT.md) registra limites.

Sem iPhone, PWA, anúncios, assinaturas, premium, IA ou publicação em lojas. Duração/intervalos são dados de organização, sem avaliação médica de alimentação suficiente.
