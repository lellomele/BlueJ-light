; BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained.
#ifndef ImagePath
  #error ImagePath is required
#endif
#ifndef OutputPath
  #error OutputPath is required
#endif
#ifndef Variant
  #define Variant "completo"
#endif
[Setup]
AppId={{A79AEAFB-E734-44E2-88C3-0D6104555131}
AppName=BlueJ light
AppVersion=5.5.3
CreateUninstallRegKey=not IsVerificationMode
AppVerName=BlueJ light 5.5.3
AppPublisher=Prof. Ing. Raffaele Mele
AppCopyright=© 2026 - Prof. Ing. Raffaele Mele
VersionInfoVersion=5.5.3.0
VersionInfoDescription=BlueJ light 5.5.3 Windows x64 ({#Variant})
VersionInfoCopyright=© 2026 - Prof. Ing. Raffaele Mele
DefaultDirName={autopf}\BlueJ light
DefaultGroupName=BlueJ light
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupArchitecture=x64
MinVersion=10.0
WizardStyle=modern
DisableProgramGroupPage=yes
SetupIconFile=..\..\bluej\icons\bluej-light.ico
UninstallDisplayIcon={app}\BlueJ light.exe
LicenseFile=..\..\LICENSE.txt
#if Variant == "senza-JDK"
InfoBeforeFile=no-jdk-info.txt
#endif
OutputDir={#OutputPath}
OutputBaseFilename=BlueJ-light-5.5.3-win64-{#Variant}
Compression=lzma2/max
SolidCompression=yes
CloseApplications=yes
RestartApplications=no
SignedUninstaller=no

[Languages]
Name: italian; MessagesFile: compiler:Languages\Italian.isl
Name: english; MessagesFile: compiler:Default.isl

[CustomMessages]
italian.DesktopIcon=Crea un collegamento sul desktop
italian.SelectJdk=Seleziona JDK 21...
italian.Launch=Avvia BlueJ light
english.DesktopIcon=Create a desktop shortcut
english.SelectJdk=Select JDK 21...
english.Launch=Start BlueJ light

[Tasks]
Name: desktopicon; Description: "{cm:DesktopIcon}"; Flags: unchecked

[Files]
Source: "{#ImagePath}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\BlueJ light"; Filename: "{app}\BlueJ light.exe"; WorkingDir: "{app}"; Check: not IsVerificationMode
Name: "{autodesktop}\BlueJ light"; Filename: "{app}\BlueJ light.exe"; WorkingDir: "{app}"; Tasks: desktopicon; Check: not IsVerificationMode
#if Variant == "senza-JDK"
Name: "{group}\{cm:SelectJdk}"; Filename: "{app}\BlueJ light.exe"; Parameters: "/selectjdk"; WorkingDir: "{app}"; Check: not IsVerificationMode
#endif

[Run]
Filename: "{app}\BlueJ light.exe"; WorkingDir: "{app}"; Description: "{cm:Launch}"; Flags: nowait postinstall skipifsilent

[InstallDelete]
Type: filesandordirs; Name: "{app}\runtime"
Type: files; Name: "{app}\app\extensions2\submitter.jar"

[Code]
function IsVerificationMode: Boolean;
begin
  Result := ExpandConstant('{param:verify|0}') = '1';
end;
