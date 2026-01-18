# 🚀 Deploying FixHomi Auth Service to Render

This guide walks you through deploying the FixHomi Auth Service to Render using Docker.

## 📋 Prerequisites

1. **GitHub Account** - Your code should be in a GitHub repository
2. **Render Account** - Sign up at [render.com](https://render.com) (free tier available)
3. **Environment Variables Ready** - Have your email, Twilio, and OAuth credentials ready

## 🎯 Quick Deploy Steps

### Option 1: Using Render Blueprint (Recommended - Fastest)

1. **Push Code to GitHub**
   ```bash
   git add Dockerfile .dockerignore render.yaml
   git commit -m "Add Render deployment configuration"
   git push origin main
   ```

2. **Deploy via Render Dashboard**
   - Go to [Render Dashboard](https://dashboard.render.com/)
   - Click **"New"** → **"Blueprint"**
   - Connect your GitHub repository
   - Select the repository containing `render.yaml`
   - Click **"Apply"**
   - Render will automatically:
     - Create a PostgreSQL database
     - Create the web service
     - Set up environment variables
     - Deploy your app

3. **Configure Secrets** (After deployment)
   - Go to your web service in Render dashboard
   - Navigate to **"Environment"** tab
   - Add these sensitive values manually:
     ```
     MAIL_USERNAME=your-email@gmail.com
     MAIL_PASSWORD=your-app-password
     TWILIO_ACCOUNT_SID=your-sid
     TWILIO_AUTH_TOKEN=your-token
     TWILIO_PHONE_NUMBER=+1234567890
     OAUTH2_GOOGLE_CLIENT_ID=your-client-id
     OAUTH2_GOOGLE_CLIENT_SECRET=your-client-secret
     ```
   - Click **"Save Changes"** - Render will redeploy automatically

### Option 2: Manual Deployment

1. **Push Code to GitHub**
   ```bash
   git add Dockerfile .dockerignore
   git commit -m "Add Docker support"
   git push origin main
   ```

2. **Create PostgreSQL Database**
   - In Render Dashboard, click **"New"** → **"PostgreSQL"**
   - Name: `fixhomi-auth-db`
   - Database: `fixhomi_auth`
   - Select plan (Free tier available)
   - Click **"Create Database"**
   - Save the connection details (provided in "Info" tab)

3. **Create Web Service**
   - Click **"New"** → **"Web Service"**
   - Connect your GitHub repository
   - Configure:
     - **Name**: `fixhomi-auth-service`
     - **Environment**: `Docker`
     - **Region**: Choose closest to your users
     - **Branch**: `main`
     - **Plan**: Free or paid
   - Click **"Create Web Service"**

4. **Add Environment Variables**
   - In your web service, go to **"Environment"** tab
   - Add all variables from the list below
   - Click **"Save Changes"**

## 🔐 Required Environment Variables

### Application Settings
```env
SPRING_PROFILES_ACTIVE=prod
PORT=8080
```

### JWT Configuration
```env
JWT_SECRET=your-super-secret-key-min-256-bits
JWT_EXPIRATION_MS=86400000
JWT_REFRESH_EXPIRATION_MS=604800000
```

### Database (Auto-populated from Render PostgreSQL)
```env
DATABASE_HOST=<from-render-postgres>
DATABASE_PORT=5432
DATABASE_NAME=fixhomi_auth
DATABASE_USERNAME=<from-render-postgres>
DATABASE_PASSWORD=<from-render-postgres>
```

**⚠️ Important:** Your app uses `DATABASE_USERNAME` (not `DATABASE_USER`)

### Email (Gmail Example)
```env
EMAIL_PROVIDER=brevo
BREVO_API_KEY=xkeysib-your-api-key
BREVO_SENDER_EMAIL=noreply@fixhomi.com
BREVO_SENDER_NAME=FixHomi
```

**Brevo Setup (formerly SendinBlue):**
- Sign up at https://www.brevo.com/ (free tier available)
- Go to Settings → API Keys → Create new key
- Use the key above

**For development/testing:** Set `EMAIL_PROVIDER=stub` (emails logged, not sent)

SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID=your-account-sid
TWILIO_AUTH_TOKEN=your-auth-token
TWILIO_PHONE_NUMBER=+1234567890
```

**Twilio Setup:**
- Sign up at https://www.twilio.com/
- Get free trial credits ($15 credit)
- Get credentials from Twilio Console

**For development/testing:** Set `SMS_PROVIDER=stub` (SMS logged, not sent)
- Get free trial credits

#### Web OAuth
```env
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret
```

#### Mobile OAuth (React Native)
```env
GOOGLE_IOS_CLIENT_ID=your-ios-client-id
GOOGLE_ANDROID_CLIENT_ID=your-android-client-id
```

**Google OAuth Setup:**
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create new project or select existing
3. Enable Google+ API
4. Create OAuth 2.0 credentials:
   - **Web application** - for web/backend
   - **iOS** - for React Native iOS app
   - **Android** - for React Native Android app
5. Add authorized redirect URI: `https://your-app.onrender.com/oauth2/callback/google
3. Enable Google+ API
4. Create OAuth 2.0 credentials
5. Add authorized redirect URI: `https://your-app.onrender.com/api/auth/oauth2/google/callback`

## ✅ Verify Deployment

1. **Check Build Logs**
   - In Render dashboard, go to your service
   - Click **"Logs"** tab
   - Watch for successful build and startup messages

2. **Test Health Endpoint**
   ```bash
   curl https://your-app.onrender.com/actuator/health
   ```
   
   Expected response:
   ```json
   {"status":"UP"}
   ```

3. **Test API Endpoints**
   - Use the Postman collection in `/postman` folder
   - Update base URL to your Render URL
   - Test registration, login, etc.

## 🔄 Continuous Deployment

Render automatically redeploys when you push to your main branch:

```bash
git add .
git commit -m "Your changes"
git push origin main
```

Render will:
1. Detect the push
2. Build new Docker image
3. Run health checks
4. Deploy with zero-downtime

## 📊 Monitoring & Logs

- **View Logs**: Dashboard → Your Service → Logs tab
- **Metrics**: Dashboard → Your Service → Metrics tab
- **Health**: Your app URL + `/actuator/health`

## 🐛 Troubleshooting

### Build Fails
- Check Dockerfile syntax
- Verify pom.xml is valid
- Check build logs in Render dashboard

### App Crashes on Startup
- Verify all required environment variables are set
- Check database connection details
- Review application logs

### Database Connection Issues
- Ensure DATABASE_HOST uses internal connection string
- Verify database is in same region as web service
- Check security group settings

### Email/SMS Not Working
- Verify credentials are correct
- Check Gmail allows "Less secure apps" or use App Password
- Verify Twilio account is active and has credits

## 🎓 For Other Developers

Share your Render app URL with the team:
```
https://fixhomi-auth-service.onrender.com
```

They can use this directly without local setup:
- No need to clone repository
- No need to install Java/Maven
- No need to configure database
- Just use the API endpoints

Update your React Native/Node.js apps to point to this URL:
```javascript
const API_BASE_URL = 'https://fixhomi-auth-service.onrender.com/api';
```

## 💰 Pricing

**Free Tier Limits:**
- 750 hours/month (enough for 1 service running 24/7)
- Apps spin down after 15 min of inactivity (cold starts ~30s)
- 100GB bandwidth/month
- Free PostgreSQL database (90 days retention)

**Paid Plans** (to avoid cold starts):
- Starter: $7/month per service
- Always-on, no cold starts
- More bandwidth and resources

## 📚 Additional Resources

- [Render Documentation](https://render.com/docs)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Spring Boot on Render](https://render.com/docs/deploy-spring-boot)

## 🤝 Need Help?

Check the logs first, then:
1. Review environment variables
2. Test endpoints with Postman
3. Check database connectivity
4. Review Render documentation
