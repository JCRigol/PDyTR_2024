import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

# Load CSV data
data = pd.read_csv('comm_times.csv', header=None, names=['Run', 'BufferSize', 'CommTime'])

# Group data by buffer size
grouped_data = data.groupby('BufferSize')

# Calculate means and standard deviations
means = grouped_data['CommTime'].mean().reset_index()
std_devs = grouped_data['CommTime'].std().reset_index()

# Merge mean and std_dev dataframes for seaborn use
stats = pd.merge(means, std_devs, on='BufferSize', suffixes=('_mean', '_std'))

# Create the plot using seaborn
plt.figure(figsize=(10, 6))
sns.set_theme(style='whitegrid')

# Plot with seaborn for aesthetics
sns.lineplot(x='BufferSize', y='CommTime_mean', data=stats, marker='o', label='Mean Comm Time')

# Add error bars manually for standard deviation
plt.errorbar(stats['BufferSize'], stats['CommTime_mean'], yerr=stats['CommTime_std'], fmt='none', capsize=5)

# Set log scale and labels
plt.xscale('log')
plt.xlabel('Buffer Size (log scale)')
plt.ylabel('Communication Time (seconds)')
plt.title('Mean Communication Time with Standard Deviation')

# Show the plot
plt.legend()
plt.show()
