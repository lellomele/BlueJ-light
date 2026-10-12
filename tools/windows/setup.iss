; BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-12. GNU GPLv2 with Classpath Exception; original notices retained.
#ifndef ImagePath
  #error ImagePath is required
#endif
#ifndef OutputPath
  #error OutputPath is required
#endif
#ifndef Variant
  #define Variant "with-jdk"
#endif
[Setup]
AppId={{A79AEAFB-E734-44E2-88C3-0D6104555131}
AppName=BlueJ light
AppVersion=5.7.0
CreateUninstallRegKey=not IsVerificationMode
AppVerName=BlueJ light 5.7.0
AppPublisher=Prof. Ing. Raffaele Mele
AppCopyright=© 2026 - Prof. Ing. Raffaele Mele
VersionInfoVersion=5.7.0.0
VersionInfoDescription=BlueJ light 5.7.0 Windows x64 ({#Variant})
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
#if Variant == "without-jdk"
InfoBeforeFile=no-jdk-info.txt
#endif
OutputDir={#OutputPath}
OutputBaseFilename=BlueJ-light-5.7.0-win64-{#Variant}
Compression=lzma2/max
SolidCompression=yes
CloseApplications=yes
RestartApplications=no
ChangesAssociations=not IsVerificationMode
SignedUninstaller=no

[Languages]
Name: italian; MessagesFile: compiler:Languages\Italian.isl
Name: english; MessagesFile: compiler:Default.isl

[CustomMessages]
italian.DesktopIcon=Crea un collegamento sul desktop
italian.SelectJdk=Seleziona JDK 21...
italian.Launch=Avvia BlueJ light
italian.RegisterBluej=Registra BlueJ light in "Apri con" per i file di progetto .bluej
italian.ProjectFile=Progetto BlueJ
italian.ApplicationDescription=Ambiente di sviluppo Java e apertura dei progetti BlueJ
italian.ChooseDefaults=Apri le impostazioni Windows per scegliere l'app predefinita per .bluej
english.DesktopIcon=Create a desktop shortcut
english.SelectJdk=Select JDK 21...
english.Launch=Start BlueJ light
english.RegisterBluej=Register BlueJ light in "Open with" for .bluej project files
english.ProjectFile=BlueJ Project
english.ApplicationDescription=Java development environment and BlueJ project support
english.ChooseDefaults=Open Windows settings to choose the default app for .bluej

[Tasks]
Name: desktopicon; Description: "{cm:DesktopIcon}"; Flags: unchecked
Name: registerbluej; Description: "{cm:RegisterBluej}"

[Files]
Source: "{#ImagePath}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\BlueJ light"; Filename: "{app}\BlueJ light.exe"; WorkingDir: "{app}"; Check: not IsVerificationMode
Name: "{autodesktop}\BlueJ light"; Filename: "{app}\BlueJ light.exe"; WorkingDir: "{app}"; Tasks: desktopicon; Check: not IsVerificationMode
#if Variant == "without-jdk"
Name: "{group}\{cm:SelectJdk}"; Filename: "{app}\BlueJ light.exe"; Parameters: "/selectjdk"; WorkingDir: "{app}"; Check: not IsVerificationMode
#endif

[Run]
Filename: "{app}\BlueJ light.exe"; WorkingDir: "{app}"; Description: "{cm:Launch}"; Flags: nowait postinstall skipifsilent
Filename: "ms-settings:defaultapps"; Description: "{cm:ChooseDefaults}"; Flags: shellexec nowait postinstall skipifsilent unchecked; Tasks: registerbluej; Check: not IsVerificationMode

[Registry]
Root: HKA; Subkey: "{code:AssociationKey|Classes\.bluej\OpenWithProgids}"; ValueType: string; ValueName: "BlueJLight.Project"; ValueData: ""; Flags: uninsdeletevalue; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|Classes\BlueJLight.Project}"; ValueType: string; ValueName: ""; ValueData: "{cm:ProjectFile}"; Flags: uninsdeletekey; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|Classes\BlueJLight.Project\DefaultIcon}"; ValueType: string; ValueName: ""; ValueData: """{app}\BlueJ light.exe"",0"; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|Classes\BlueJLight.Project\shell\open\command}"; ValueType: string; ValueName: ""; ValueData: """{app}\BlueJ light.exe"" ""%1"""; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|Classes\Applications\BlueJ light.exe}"; ValueType: string; ValueName: "FriendlyAppName"; ValueData: "BlueJ light"; Flags: uninsdeletekey; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|Classes\Applications\BlueJ light.exe\SupportedTypes}"; ValueType: string; ValueName: ".bluej"; ValueData: ""; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|Classes\Applications\BlueJ light.exe\DefaultIcon}"; ValueType: string; ValueName: ""; ValueData: """{app}\BlueJ light.exe"",0"; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|Classes\Applications\BlueJ light.exe\shell\open\command}"; ValueType: string; ValueName: ""; ValueData: """{app}\BlueJ light.exe"" ""%1"""; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|BlueJLight\Capabilities}"; ValueType: string; ValueName: "ApplicationName"; ValueData: "BlueJ light"; Flags: uninsdeletekey; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|BlueJLight\Capabilities}"; ValueType: string; ValueName: "ApplicationDescription"; ValueData: "{cm:ApplicationDescription}"; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|BlueJLight\Capabilities}"; ValueType: string; ValueName: "ApplicationIcon"; ValueData: """{app}\BlueJ light.exe"",0"; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|BlueJLight\Capabilities\FileAssociations}"; ValueType: string; ValueName: ".bluej"; ValueData: "BlueJLight.Project"; Tasks: registerbluej
Root: HKA; Subkey: "{code:AssociationKey|RegisteredApplications}"; ValueType: string; ValueName: "BlueJ light"; ValueData: "{code:AssociationKey|BlueJLight\Capabilities}"; Flags: uninsdeletevalue; Tasks: registerbluej

[InstallDelete]
Type: filesandordirs; Name: "{app}\runtime"
Type: files; Name: "{app}\app\extensions2\submitter.jar"
Type: files; Name: "{app}\app\lang-stride.jar"
Type: files; Name: "{app}\app\chinese\frame-labels"
Type: files; Name: "{app}\app\czech\frame-labels"
Type: files; Name: "{app}\app\dutch\frame-labels"
Type: files; Name: "{app}\app\english\frame-labels"
Type: files; Name: "{app}\app\german\frame-labels"
Type: files; Name: "{app}\app\italian\frame-labels"
Type: files; Name: "{app}\app\korean\frame-labels"
Type: files; Name: "{app}\app\polish\frame-labels"
Type: files; Name: "{app}\app\portuguese\frame-labels"
Type: files; Name: "{app}\app\slovak\frame-labels"
Type: files; Name: "{app}\app\stylesheets\editor-banners.css"
Type: files; Name: "{app}\app\stylesheets\editor-catalogue.css"
Type: files; Name: "{app}\app\stylesheets\editor-error-bar.css"
Type: files; Name: "{app}\app\stylesheets\editor-expression.css"
Type: files; Name: "{app}\app\stylesheets\editor-help.css"
Type: files; Name: "{app}\app\stylesheets\editor-selection.css"
Type: files; Name: "{app}\app\stylesheets\editor-slot-choice.css"
Type: files; Name: "{app}\app\stylesheets\frame-style.css"
Type: files; Name: "{app}\doc\Greenfoot3\design-issues.txt"
Type: files; Name: "{app}\doc\Greenfoot3\features.txt"
Type: files; Name: "{app}\doc\Greenfoot3\greenfoot-3-architecture.txt"
Type: files; Name: "{app}\doc\Greenfoot3\java-differences.txt"
Type: files; Name: "{app}\doc\Greenfoot3\known-bugs"
Type: files; Name: "{app}\doc\Greenfoot3\need-testing"
Type: files; Name: "{app}\doc\Greenfoot3\tasks.txt"
Type: files; Name: "{app}\doc\Greenfoot3\wish-list"
Type: files; Name: "{app}\doc\blackbox\Makefile"
Type: files; Name: "{app}\doc\blackbox\blackbox.html"
Type: files; Name: "{app}\doc\blackbox\blackbox.pdf"
Type: files; Name: "{app}\doc\blackbox\blackbox.tex"
Type: files; Name: "{app}\doc\blackbox\data-loss.png"
Type: files; Name: "{app}\doc\blackbox\emacsbuild"
Type: files; Name: "{app}\doc\blackbox\inject_schema.rb"
Type: files; Name: "{app}\doc\blackbox\models.pdf"
Type: files; Name: "{app}\doc\blackbox\new_user_instructions.txt"
Type: files; Name: "{app}\doc\blackbox\server_setup_instructions.txt"
Type: files; Name: "{app}\doc\BlueJ-architecture-and-design.txt"
Type: files; Name: "{app}\doc\BlueJ-parser.txt"
Type: files; Name: "{app}\doc\BlueJ-reference.doc"
Type: files; Name: "{app}\doc\HOWTO.announce-release"
Type: files; Name: "{app}\doc\HOWTO.make-mac-release"
Type: files; Name: "{app}\doc\HOWTO.make-release"
Type: files; Name: "{app}\doc\HOWTO.make-win-version"
Type: files; Name: "{app}\doc\key-bindings.txt"
Type: files; Name: "{app}\doc\README.TXT"
Type: files; Name: "{app}\doc\sourcecode-styleguide.txt"
Type: files; Name: "{app}\doc\teamwork-tutorial.doc"
Type: files; Name: "{app}\doc\threadchecker.txt"
Type: files; Name: "{app}\doc\threading-issues.txt"
Type: files; Name: "{app}\doc\translations.txt"
Type: files; Name: "{app}\LEGGIMI-JDK.txt"

[Code]
function IsVerificationMode: Boolean;
begin
  Result := ExpandConstant('{param:verify|0}') = '1';
end;

function AssociationKey(RelativeKey: String): String;
begin
  if IsVerificationMode then
    Result := 'Software\BlueJLightInstallerVerification\' + RelativeKey
  else
    Result := 'Software\' + RelativeKey;
end;
