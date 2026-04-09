# WSL General Cheat Sheet

## What WSL Is

WSL lets you run a Linux environment directly on Windows.

Common use cases:

- run Linux shells such as `bash` or `zsh`
- install Linux packages with `apt`
- build and test projects in a Linux-like environment
- access Windows files from Linux and Linux files from Windows

## Open WSL

From PowerShell or Command Prompt:

```powershell
wsl
wsl -d kali-linux
wsl -d Ubuntu
wsl -l -v
```

Meaning:

- `wsl`: open the default distro
- `wsl -d <name>`: open a specific distro
- `wsl -l -v`: list installed distros and their WSL version

## Start In A Specific Folder

From PowerShell:

```powershell
wsl -d kali-linux --cd /mnt/d/BT/RISC-V-disassembler
```

## Run One Command Without Staying In WSL

From PowerShell:

```powershell
wsl -d kali-linux pwd
wsl -d kali-linux ls
wsl -d kali-linux bash -lc "uname -a && whoami"
```

Useful when you want to run one command and return to Windows immediately.

## Shutdown And Restart

From PowerShell:

```powershell
wsl --shutdown
wsl --terminate kali-linux
```

Meaning:

- `wsl --shutdown`: stop all running WSL distros
- `wsl --terminate <name>`: stop one distro only

## Basic Navigation Inside WSL

Inside WSL:

```bash
pwd
ls
ls -la
cd ~
cd /tmp
cd /mnt/d
mkdir my-folder
rm file.txt
rm -r my-folder
cp a.txt b.txt
mv old.txt new.txt
```

## Important Paths

Inside WSL:

```bash
~
```

This is your Linux home folder, for example:

```bash
/home/your-user
```

Windows drives are mounted under:

```bash
/mnt/c
/mnt/d
/mnt/e
```

Example:

```bash
cd /mnt/d/BT/RISC-V-disassembler
```

## Convert Paths Between Windows And WSL

Inside WSL:

```bash
wslpath "D:\BT\RISC-V-disassembler"
```

Output:

```bash
/mnt/d/BT/RISC-V-disassembler
```

From WSL path to Windows path:

```bash
wslpath -w /mnt/d/BT/RISC-V-disassembler
```

## Open Windows Explorer From WSL

Inside WSL:

```bash
explorer.exe .
```

Open the current Linux folder in Windows Explorer.

## Open The Current Project In VS Code

Inside WSL:

```bash
code .
```

This works if VS Code and the WSL extension are installed.

## Update Packages

For Debian, Ubuntu, and Kali:

```bash
sudo apt update
sudo apt upgrade
```

Install packages:

```bash
sudo apt install git curl wget build-essential
```

Search for packages:

```bash
apt search gcc
apt search riscv
```

## Check System Info

Inside WSL:

```bash
uname -a
cat /etc/os-release
whoami
pwd
df -h
free -h
```

## Process Management

Inside WSL:

```bash
ps aux
top
htop
kill <pid>
pkill python
```

If `htop` is missing:

```bash
sudo apt install htop
```

## Networking

Inside WSL:

```bash
ip a
ping google.com
curl ifconfig.me
ss -tulpn
```

Useful for checking IP addresses, connectivity, and open ports.

## File Search And Text Search

Inside WSL:

```bash
find . -name "*.c"
find . -name "*.s"
grep -R "main" .
```

Faster alternatives if installed:

```bash
rg "main"
rg --files
```

Install `ripgrep`:

```bash
sudo apt install ripgrep
```

## Permissions

Inside WSL:

```bash
chmod +x script.sh
ls -l
```

Run a script:

```bash
./script.sh
bash script.sh
```

## Shell Configuration

Common config files:

```bash
~/.bashrc
~/.profile
~/.zshrc
```

Reload config:

```bash
source ~/.bashrc
```

Example aliases:

```bash
alias ll='ls -la'
alias croot='cd /mnt/d/BT/RISC-V-disassembler'
```

## Git In WSL

Inside WSL:

```bash
git status
git pull
git add .
git commit -m "message"
git push
```

Set your identity:

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

## Common Workflow

From PowerShell:

```powershell
wsl -d kali-linux --cd /mnt/d/BT/RISC-V-disassembler
```

Then inside WSL:

```bash
git status
ls
code .
```

## Useful Troubleshooting

List distros:

```powershell
wsl -l -v
```

Restart WSL:

```powershell
wsl --shutdown
```

Check distro version:

```powershell
wsl --status
```

If a distro opens with a startup banner you do not want:

```bash
touch ~/.hushlogin
```

## Good Habits

- Keep project source code on `/mnt/c` or `/mnt/d` if you want easy access from Windows tools.
- Keep Linux-only config and scripts in your home folder `~`.
- Prefer running Linux builds inside WSL instead of mixing Windows and Linux toolchains.
- Use `code .` from WSL when working on Linux-based projects.
