import base64, json, os, re, sys

pack = json.load(open(sys.argv[1]))
root = sys.argv[2]
pkg, name = pack["packageName"], pack["appName"]

if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", pkg):
    sys.exit("package tidak valid")

allowed = re.compile(
    r"^app/src/main/(assets/[A-Za-z0-9_./-]+|res/drawable/ic_launcher\.png|res/values/strings\.xml)$")
for path, data in pack["files"].items():
    if ".." in path or not allowed.match(path):
        sys.exit("path ditolak: " + path)
    full = os.path.join(root, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "wb") as f:
        f.write(base64.b64decode(data))

g = os.path.join(root, "app/build.gradle")
s = open(g).read().replace('applicationId "com.web2apk.template"', 'applicationId "%s"' % pkg)
open(g, "w").write(s)

m = os.path.join(root, "app/src/main/AndroidManifest.xml")
s = open(m).read()
s = re.sub(r'\s+package="[^"]*"', "", s)
s = re.sub(r"\s*<uses-sdk[^>]*/>", "", s)
s = re.sub(r'\s+android:versionCode="[^"]*"', "", s)
s = re.sub(r'\s+android:versionName="[^"]*"', "", s)
open(m, "w").write(s)

slug = re.sub(r"[^A-Za-z0-9]+", "-", name).strip("-") or "app"
open("/tmp/slug", "w").write(slug[:40])
