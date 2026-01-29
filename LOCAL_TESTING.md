# 🚀 Quick Start Guide - Cloud Cost Optimizer

## Copy-Paste Commands to Run Locally

### 1. Start the Backend (1 command)

```bash
cd backend && mvn spring-boot:run
```

✅ That's it! Backend runs on **http://localhost:8080**
- No database setup needed (H2 in-memory)
- No cloud credentials needed
- No configuration files to edit

### 2. Load the Browser Extension (3 clicks)

1. Open Chrome/Edge → Go to `chrome://extensions`
2. Enable "Developer mode" (toggle switch)
3. Click "Load unpacked" → Select the `extension/` folder

✅ Extension is now loaded!

### 3. Test It (1 click)

Open this file in your browser:
```bash
open extension/test-page.html
```

or just drag `extension/test-page.html` into your browser window.

---

## What You Should See

✅ **Backend logs** show: `Started CloudOptimizerApplication in X seconds`
✅ **Extension icon** appears in browser toolbar
✅ **Test page** shows 4 simulated cloud resources
✅ **Browser console** (F12) shows extension logs
✅ **Extension should detect** the test resources and show recommendations

---

## Quick Links

| What | URL |
|------|-----|
| Backend Health | http://localhost:8080/actuator/health |
| API Docs | http://localhost:8080/swagger-ui.html |
| Database Console | http://localhost:8080/h2-console |
| Test Page | `extension/test-page.html` |
| Extension Management | `chrome://extensions` |

---

## Troubleshooting

### Backend won't start?
```bash
# Check if port 8080 is in use
lsof -i :8080  # macOS/Linux

# Use different port
SERVER_PORT=8081 mvn spring-boot:run
```

### Extension not working?
1. Check backend is running: `curl http://localhost:8080/actuator/health`
2. Reload extension: Go to `chrome://extensions` → Click reload button
3. Check browser console (F12) for errors
4. Try closing and reopening the test page

### Can't see recommendations?
- Open DevTools (F12)
- Go to Console tab
- Look for "Cloud Optimizer" messages
- Check Network tab for API calls to localhost:8080

---

## Test the API Directly

```bash
# Health check
curl http://localhost:8080/actuator/health

# Analyze a resource
curl -X POST http://localhost:8080/api/extension/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "AZURE",
    "resourceId": "test-vm-01",
    "resourceType": "COMPUTE",
    "region": "eastus",
    "detectedConfig": {"vcpu": 4, "memoryGb": 16}
  }'
```

---

## Development Workflow

```bash
# 1. Start backend (leave running)
cd backend
mvn spring-boot:run

# 2. Make code changes to Java files
# → Auto-reloads with Spring DevTools

# 3. Make changes to extension files
# → Go to chrome://extensions and click reload

# 4. Test changes on test page
open extension/test-page.html
```

---

## What's Pre-Configured for Local Dev?

✅ H2 in-memory database (no setup)
✅ Mock cloud provider APIs (no credentials)
✅ Disabled authentication (no login)
✅ CORS enabled for browser extension
✅ Test data auto-generated
✅ Hot reload for backend code
✅ Swagger UI for API testing

---

## File Structure

```
cloud-optimization-browser-extension/
├── backend/                  # Spring Boot API
│   ├── pom.xml              # Dependencies
│   ├── src/main/            # Java source code
│   └── src/test/            # Tests
├── extension/               # Browser extension
│   ├── manifest.json        # Extension config
│   ├── src/                 # Extension code
│   ├── test-page.html       # Local test page ← START HERE
│   └── assets/              # Icons
├── README.md                # Full documentation
├── CLAUDE_PROMPT.md         # AI development guide
└── LOCAL_TESTING.md         # This file
```

---

## Next Steps

Once you've verified it works locally:

1. **Customize the Extension**
   - Edit files in `extension/src/`
   - Reload extension in `chrome://extensions`

2. **Customize the Backend**
   - Edit files in `backend/src/main/java/`
   - Code auto-reloads

3. **Add Features**
   - Follow patterns in CLAUDE_PROMPT.md
   - Keep everything locally testable

4. **Deploy to Production**
   - See README.md "Enterprise Deployment" section
   - Update configuration for production URLs
   - Enable authentication and real cloud APIs

---

## Help & Documentation

- **Full Documentation**: See [README.md](../README.md)
- **AI Development Guide**: See [CLAUDE_PROMPT.md](../CLAUDE_PROMPT.md)
- **Architecture Diagrams**: In README.md
- **API Reference**: http://localhost:8080/swagger-ui.html (when running)

---

## Success Checklist

Before moving to production deployment:

- [ ] Backend starts without errors
- [ ] Extension loads in browser
- [ ] Test page shows resources
- [ ] Extension detects test resources
- [ ] API calls appear in Network tab
- [ ] Backend logs show API requests
- [ ] Health endpoint returns 200 OK
- [ ] Swagger UI shows API documentation

---

**You're ready to develop! 🎉**

Everything works locally, no cloud setup required.
