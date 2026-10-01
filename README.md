# Amamenta Bebê

Aplicativo Android nativo, offline e sem conta para registrar mamadas e lembrar o próximo horário. O intervalo é uma preferência de organização familiar, não uma recomendação médica.

## Funcionalidades

- Registro imediato com **Mamou agora**, protegido contra toque duplo acidental.
- Próximo horário, contagem regressiva e resumo diário.
- Intervalos predefinidos ou personalizados.
- Histórico agrupado por dia, com edição, exclusão, lado, quantidade e observação.
- Alarmes pontuais com `AlarmManager`, inclusive em Doze, e reagendamento após reiniciar.
- Notificação com ações **Mamou agora**, **Adiar 10 min** e **Adiar 20 min**.
- Status e atalhos para permissões de notificações e alarmes exatos.
- Temas claro, escuro e conforme o sistema.
- Room e DataStore locais; nenhum tracker, anúncio ou envio de dados.

## Compilar

Requisitos: JDK 17 e Android SDK 35.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

O APK será criado em `app/build/outputs/apk/debug/app-debug.apk`.

## APK pelo GitHub Actions

O workflow `.github/workflows/build-apk.yml` roda em pushes para `main`/`master` e manualmente em **Actions → Build Android APK → Run workflow**. Ao concluir, abra a execução e baixe o artifact **Amamenta-Bebe-APK**, que contém `amamenta-bebe.apk`.

## Permissões Android

- `POST_NOTIFICATIONS`: exibir o lembrete no Android 13+.
- `SCHEDULE_EXACT_ALARM`: permitir horário exato no Android 12+; sem o acesso, o app usa alarme inexato e informa a limitação.
- `RECEIVE_BOOT_COMPLETED`: restaurar um lembrete futuro após reiniciar.
- `VIBRATE`: vibrar no aviso quando habilitado.

As configurações de canais do Android têm precedência sobre som e vibração escolhidos no aplicativo.
