FROM node:22-alpine
WORKDIR /app
COPY --chown=node:node package.json server.mjs ./
COPY --chown=node:node public ./public
USER node
ENV DEMO_HOST=0.0.0.0 DEMO_PORT=3000
EXPOSE 3000
CMD ["node", "server.mjs"]
