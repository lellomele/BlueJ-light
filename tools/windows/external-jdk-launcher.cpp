/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
#ifndef UNICODE
#define UNICODE
#endif
#define _UNICODE
#define _WIN32_WINNT 0x0601
#include <windows.h>
#include <shlobj.h>
#include <shobjidl.h>
#include <shellapi.h>
#include <filesystem>
#include <fstream>
#include <string>
#include <vector>
#include <stdexcept>

namespace fs = std::filesystem;
using std::wstring;
const wchar_t* runtimeKey = L"Software\\BlueJLight\\Runtime";

wstring utf8(const std::string& text) {
    int length = MultiByteToWideChar(CP_UTF8, 0, text.data(), (int)text.size(), nullptr, 0);
    wstring result(length, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, text.data(), (int)text.size(), result.data(), length);
    return result;
}
wstring environment(const wchar_t* name) {
    DWORD length = GetEnvironmentVariableW(name, nullptr, 0);
    if (!length) return {};
    wstring value(length, L'\0');
    GetEnvironmentVariableW(name, value.data(), length);
    value.resize(value.size() - 1);
    return value;
}
bool validJdk(const fs::path& home) {
    try {
        DWORD type = 0;
        if (!GetBinaryTypeW((home / L"bin/javaw.exe").c_str(), &type) || type != SCS_64BIT_BINARY
            || !fs::exists(home / L"bin/javac.exe")) return false;
        std::ifstream release(home / L"release");
        std::string line, version, architecture;
        while (std::getline(release, line)) {
            auto equals = line.find('=');
            if (equals == std::string::npos) continue;
            auto value = line.substr(equals + 1);
            if (!value.empty() && value.back() == '\r') value.pop_back();
            if (value.size() >= 2 && value.front() == '"' && value.back() == '"') value = value.substr(1, value.size() - 2);
            if (line.substr(0, equals) == "JAVA_VERSION") version = value;
            if (line.substr(0, equals) == "OS_ARCH") architecture = value;
        }
        return std::stoi(version) == 21 && (architecture == "amd64" || architecture == "x86_64" || architecture == "x64");
    } catch (...) { return false; }
}
wstring registryValue(HKEY root, const wstring& key, const wchar_t* value) {
    wchar_t buffer[32768];
    DWORD size = sizeof(buffer);
    HKEY handle;
    if (RegOpenKeyExW(root, key.c_str(), 0, KEY_READ | KEY_WOW64_64KEY, &handle) != ERROR_SUCCESS) return {};
    LSTATUS status = RegGetValueW(handle, nullptr, value, RRF_RT_REG_SZ, nullptr, buffer, &size);
    RegCloseKey(handle);
    return status == ERROR_SUCCESS ? wstring(buffer) : wstring();
}
wstring searchRegistry(HKEY root, const wstring& key, int depth) {
    for (auto value : {L"JavaHome", L"Path", L"InstallationPath"}) {
        auto home = registryValue(root, key, value);
        if (!home.empty() && validJdk(home)) return home;
    }
    if (depth == 0) return {};
    HKEY handle;
    if (RegOpenKeyExW(root, key.c_str(), 0, KEY_READ | KEY_WOW64_64KEY, &handle) != ERROR_SUCCESS) return {};
    for (DWORD index = 0;; index++) {
        wchar_t child[256]; DWORD size = 256;
        if (RegEnumKeyExW(handle, index, child, &size, nullptr, nullptr, nullptr, nullptr) != ERROR_SUCCESS) break;
        auto found = searchRegistry(root, key + L"\\" + child, depth - 1);
        if (!found.empty()) { RegCloseKey(handle); return found; }
    }
    RegCloseKey(handle);
    return {};
}
wstring findJdk() {
    for (auto name : {L"BLUEJ_LIGHT_JDK", L"JAVA_HOME", L"JDK_HOME"}) {
        auto home = environment(name);
        if (!home.empty() && validJdk(home)) return home;
    }
    auto saved = registryValue(HKEY_CURRENT_USER, runtimeKey, L"JavaHome");
    if (!saved.empty() && validJdk(saved)) return saved;
    for (HKEY root : {HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE})
        for (auto vendor : {L"SOFTWARE\\JavaSoft\\JDK", L"SOFTWARE\\Eclipse Adoptium\\JDK",
            L"SOFTWARE\\Microsoft\\JDK", L"SOFTWARE\\BellSoft\\Liberica", L"SOFTWARE\\Azul Systems\\Zulu"}) {
            auto found = searchRegistry(root, vendor, 4);
            if (!found.empty()) return found;
        }
    wchar_t java[32768];
    if (SearchPathW(nullptr, L"java.exe", nullptr, 32768, java, nullptr)) {
        auto home = fs::path(java).parent_path().parent_path();
        if (validJdk(home)) return home.wstring();
    }
    auto programs = environment(L"ProgramFiles");
    for (auto vendor : {L"Java", L"Eclipse Adoptium", L"Microsoft", L"BellSoft", L"Zulu"}) {
        try {
            auto folder = fs::path(programs) / vendor;
            if (!fs::is_directory(folder)) continue;
            for (const auto& candidate : fs::directory_iterator(folder))
                if (validJdk(candidate.path())) return candidate.path().wstring();
        } catch (...) { }
    }
    return {};
}
wstring chooseJdk() {
    IFileDialog* dialog = nullptr;
    if (FAILED(CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&dialog)))) return {};
    DWORD options; dialog->GetOptions(&options);
    dialog->SetOptions(options | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);
    dialog->SetTitle(L"Seleziona la cartella di un JDK 21 a 64 bit");
    wstring home;
    if (SUCCEEDED(dialog->Show(nullptr))) {
        IShellItem* item = nullptr;
        if (SUCCEEDED(dialog->GetResult(&item))) {
            PWSTR selected = nullptr;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &selected))) { home = selected; CoTaskMemFree(selected); }
            item->Release();
        }
    }
    dialog->Release();
    return home;
}
void saveJdk(const wstring& home) {
    HKEY key;
    if (RegCreateKeyExW(HKEY_CURRENT_USER, runtimeKey, 0, nullptr, 0, KEY_WRITE, nullptr, &key, nullptr) == ERROR_SUCCESS) {
        RegSetValueExW(key, L"JavaHome", 0, REG_SZ, (const BYTE*)home.c_str(), (DWORD)((home.size() + 1) * sizeof(wchar_t)));
        RegCloseKey(key);
    }
}
// Follow Windows' backslash/quote rules, including trailing backslashes.
wstring quote(const wstring& value) {
    wstring result = L"\""; size_t slashes = 0;
    for (wchar_t c : value) {
        if (c == L'\\') { slashes++; continue; }
        result.append(c == L'"' ? slashes * 2 + 1 : slashes, L'\\');
        slashes = 0; result += c;
    }
    result.append(slashes * 2, L'\\');
    return result + L'"';
}
void replaceAll(wstring& value, const wstring& from, const wstring& to) {
    size_t at = 0;
    while ((at = value.find(from, at)) != wstring::npos) { value.replace(at, from.size(), to); at += to.size(); }
}
int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
    try {
        wchar_t executable[32768];
        if (!GetModuleFileNameW(nullptr, executable, 32768)) return 1;
        fs::path root = fs::path(executable).parent_path(), app = root / L"app";
        int argc = 0;
        LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
        if (!argv) return 1;
        bool select = false;
        std::vector<wstring> userArguments;
        for (int i = 1; i < argc; i++) {
            wstring argument(argv[i]);
            if (argument == L"/selectjdk") select = true;
            else if (argument.rfind(L"/checkjdk=", 0) == 0) {
                bool valid = validJdk(argument.substr(10)); LocalFree(argv); return valid ? 0 : 2;
            } else userArguments.push_back(argument);
        }
        LocalFree(argv);
        wstring home = select ? L"" : findJdk();
        if (home.empty()) {
            if (!select && MessageBoxW(nullptr,
                L"BlueJ light senza JDK richiede un JDK 21 a 64 bit gia' installato.\n"
                L"Il solo JRE non basta. Vuoi selezionare la cartella del JDK?\n"
                L"In alternativa puoi installare il pacchetto completo di BlueJ light.",
                L"BlueJ light", MB_YESNO | MB_ICONINFORMATION) != IDYES) return 0;
            CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
            for (;;) {
                home = chooseJdk();
                if (home.empty()) { CoUninitialize(); return 0; }
                if (validJdk(home)) break;
                MessageBoxW(nullptr, L"La cartella non contiene un JDK 21 Windows x64 valido.\nSeleziona la cartella principale del JDK, non la cartella bin.", L"BlueJ light", MB_OK | MB_ICONWARNING);
            }
            CoUninitialize();
            saveJdk(home);
        }
        if (select) return 0;
        std::ifstream config(app / L"BlueJ light.cfg");
        std::string line, section;
        wstring mainClass = L"bluej.Boot", classpath;
        std::vector<wstring> options;
        while (std::getline(config, line)) {
            if (!line.empty() && line.back() == '\r') line.pop_back();
            if (!line.empty() && line.front() == '[') { section = line; continue; }
            auto equals = line.find('=');
            if (equals == std::string::npos) continue;
            auto key = line.substr(0, equals);
            wstring value = utf8(line.substr(equals + 1));
            replaceAll(value, L"$APPDIR", app.wstring());
            if (section == "[Application]" && key == "app.mainclass") mainClass = value;
            if (section == "[Application]" && key == "app.classpath") { if (!classpath.empty()) classpath += L';'; classpath += value; }
            if (section == "[JavaOptions]" && key == "java-options") options.push_back(value);
        }
        if (classpath.empty()) throw std::runtime_error("Missing application configuration");
        fs::path java = fs::path(home) / L"bin/javaw.exe";
        wstring command = quote(java.wstring());
        for (const auto& option : options) command += L" " + quote(option);
        command += L" -classpath " + quote(classpath) + L" " + quote(mainClass);
        for (const auto& argument : userArguments) command += L" " + quote(argument);
        if (command.size() >= 32767) throw std::runtime_error("Command line too long");
        STARTUPINFOW startup{}; startup.cb = sizeof(startup);
        PROCESS_INFORMATION process{};
        if (!CreateProcessW(java.c_str(), command.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW,
                nullptr, nullptr, &startup, &process)) throw std::runtime_error("Cannot launch Java");
        CloseHandle(process.hThread);
        WaitForSingleObject(process.hProcess, INFINITE);
        DWORD result = 1; GetExitCodeProcess(process.hProcess, &result); CloseHandle(process.hProcess);
        return (int)result;
    } catch (...) {
        MessageBoxW(nullptr, L"Impossibile avviare BlueJ light.\nControlla che la cartella app sia completa e che il JDK 21 sia disponibile.", L"BlueJ light", MB_OK | MB_ICONERROR);
        return 1;
    }
}
