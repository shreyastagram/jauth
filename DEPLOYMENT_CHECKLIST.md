# ⚡ Quick Deployment Checklist

## ✅ Pre-Deployment (5 minutes)

- [ ] Push code to GitHub
  ```bash
  git add Dockerfile .dockerignore render.yaml RENDER_DEPLOYMENT_GUIDE.md
  git commit -m "Add Render deployment support"
  git push origin main
  ```

## 🚀 Render Setup (10 minutes)

1. **Sign up at render.com** (use GitHub login)

2. **Click "New" → "Blueprint"**
   - Connect your GitHub repository
   - Select the repo with `render.yaml`
   - Click "Apply"

3. **Wait for automatic setup** (~5 min)
   - ✅ PostgreSQL database created
   - ✅ Web service created
   - ✅ First deployment starts

## 🔑 Configure Secrets (5 minutes)

Go to your web service → Environment tab → Add:

```
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-gmail-app-password
TWILIO_ACCOUNT_SID=your-twilio-sid
TWILIO_AUTH_TOKEN=your-twilio-token
TWILIO_PHONE_NUMBER=+1234567890
OAUTH2_GOOGLE_CLIENT_ID=your-google-client-id
OAUTH2_GOOGLE_CLIENT_SECRET=your-google-secret
```

Click "Save Changes" → Auto-redeploy happens

## ✨ You're Live!

Your API is now at: `https://fixhomi-auth-service.onrender.com`

Test it:
```bash
curl https://fixhomi-auth-service.onrender.com/actuator/health
```

## 📱 Update Your Frontend Apps

Update API URL in React Native/Node.js:
```javascript
const API_URL = 'https://fixhomi-auth-service.onrender.com/api';
```

## 🎯 Share With Team

Send them the URL - no local setup needed!
```
https://fixhomi-auth-service.onrender.com
```

## ⚠️ Important Notes

- **Free tier**: Apps sleep after 15 min inactivity (30s cold start)
- **Upgrade to $7/month**: No cold starts, always-on
- **Auto-deploy**: Every git push triggers deployment
- **Logs**: View in Render dashboard → Logs tab

## 📊 Files Created

- `Dockerfile` - Multi-stage build optimized for Render
- `.dockerignore` - Faster builds by excluding unnecessary files
- `render.yaml` - Infrastructure as Code for one-click deploy
- `RENDER_DEPLOYMENT_GUIDE.md` - Detailed instructions
- `DEPLOYMENT_CHECKLIST.md` - This quick reference

---

**Total Setup Time: ~20 minutes**
**Result: Production-ready API accessible to your entire team**
